package com.lifelink.request;

import com.lifelink.bloodbank.BloodBank;
import com.lifelink.bloodbank.BloodBankRepository;
import com.lifelink.bloodbank.BloodInventory;
import com.lifelink.bloodbank.BloodInventoryRepository;
import com.lifelink.common.BadRequestException;
import com.lifelink.common.ConflictException;
import com.lifelink.common.ForbiddenException;
import com.lifelink.common.NotFoundException;
import com.lifelink.common.PageResponse;
import com.lifelink.donation.Donation;
import com.lifelink.donation.DonationRepository;
import com.lifelink.donor.Donor;
import com.lifelink.donor.DonorService;
import com.lifelink.hospital.Hospital;
import com.lifelink.hospital.HospitalRepository;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import com.lifelink.redis.DonorGeoService;
import com.lifelink.redis.RequestRateLimiter;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.request.dto.FulfillRequest;
import com.lifelink.request.dto.RequestMatchResponse;
import com.lifelink.request.dto.RequestResponse;
import com.lifelink.request.dto.StatusHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestService {

    static final int ELIGIBILITY_COOLDOWN_DAYS = 90;

    private final RequestRepository requestRepository;
    private final RequestMatchRepository requestMatchRepository;
    private final RequestStatusHistoryRepository historyRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bloodBankRepository;
    private final BloodInventoryRepository bloodInventoryRepository;
    private final DonationRepository donationRepository;
    private final RequestLifecycleService lifecycle;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final RequestRateLimiter rateLimiter;
    private final DonorGeoService donorGeoService;

    @Transactional
    public RequestResponse create(Long userId, CreateRequestRequest dto) {
        Hospital hospital = hospitalRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("No hospital profile for this account"));
        if (!hospital.isVerified()) {
            throw new ForbiddenException("Hospital is pending admin verification");
        }
        rateLimiter.recordRequest(hospital.getId());

        Request request = new Request();
        request.setHospital(hospital);
        request.setBloodGroup(dto.bloodGroup());
        request.setUnits(dto.units());
        request.setUrgency(dto.urgency());
        request.setNeededBy(dto.neededBy());
        request.setNotes(dto.notes());
        request.setStatus(RequestStatus.RAISED);
        requestRepository.save(request);

        lifecycle.recordRaised(request, userId);
        matchingService.matchDonors(request);
        return RequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestResponse> list(Long userId, RequestStatus status, int page, int size) {
        Hospital hospital = requireHospital(userId);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Request> requests = status == null
                ? requestRepository.findByHospitalId(hospital.getId(), pageable)
                : requestRepository.findByHospitalIdAndStatus(hospital.getId(), status, pageable);
        return PageResponse.of(requests, RequestResponse::from);
    }

    @Transactional(readOnly = true)
    public RequestResponse detail(Long userId, Long requestId) {
        return RequestResponse.from(loadOwnedRequest(userId, requestId));
    }

    @Transactional(readOnly = true)
    public List<RequestMatchResponse> matches(Long userId, Long requestId) {
        loadOwnedRequest(userId, requestId);
        return requestMatchRepository.findByRequestId(requestId).stream()
                .map(RequestMatchResponse::from)
                .toList();
    }

    @Transactional
    public RequestResponse confirmMatch(Long userId, Long requestId, Long matchId) {
        Request request = loadOwnedRequest(userId, requestId);
        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> NotFoundException.of("Match", matchId));
        if (!match.getRequest().getId().equals(requestId)) {
            throw new NotFoundException("Match " + matchId + " does not belong to request " + requestId);
        }
        // Guard (spec §3): can't confirm a donor who didn't accept
        if (match.getStatus() != MatchStatus.ACCEPTED) {
            throw new ConflictException("Donor has not accepted this match");
        }

        lifecycle.fire(request, RequestEvent.HOSPITAL_CONFIRMED, userId, null);
        match.setStatus(MatchStatus.CONFIRMED);

        Hospital hospital = request.getHospital();
        notificationService.notify(match.getDonor().getUserId(), NotificationType.MATCH_CONFIRMED,
                "You're confirmed — " + hospital.getName(),
                "Please come to %s by %s to donate %s blood."
                        .formatted(hospital.getAddress(), request.getNeededBy(), request.getBloodGroup().label()));

        for (RequestMatch other : requestMatchRepository.findByRequestId(requestId)) {
            if (!other.getId().equals(matchId) && other.getStatus() == MatchStatus.ACCEPTED) {
                notificationService.notify(other.getDonor().getUserId(), NotificationType.MATCH_COVERED,
                        "Request covered — thank you!",
                        hospital.getName() + "'s request is covered by another donor. Thanks for stepping up!");
            }
        }
        return RequestResponse.from(request);
    }

    @CacheEvict(cacheNames = DonorService.DONOR_CACHE, key = "#dto.donorId() + ':profile'",
            condition = "#dto.donorId() != null")
    @Transactional
    public RequestResponse fulfill(Long userId, Long requestId, FulfillRequest dto) {
        Request request = loadOwnedRequest(userId, requestId);
        if ((dto.donorId() == null) == (dto.bloodBankId() == null)) {
            throw new BadRequestException("Provide exactly one of donorId or bloodBankId");
        }

        Donation donation = new Donation();
        donation.setRequest(request);
        donation.setUnits(dto.units());

        if (dto.donorId() != null) {
            RequestMatch match = requestMatchRepository.findByRequestIdAndDonorUserId(requestId, dto.donorId())
                    .orElseThrow(() -> new NotFoundException("Donor was not matched to this request"));
            if (match.getStatus() != MatchStatus.CONFIRMED) {
                throw new ConflictException("Donor was not confirmed for this request");
            }
            Donor donor = match.getDonor();
            donation.setDonor(donor);

            LocalDate today = LocalDate.now();
            donor.setLastDonationDate(today);
            donor.setNextEligibleDate(today.plusDays(ELIGIBILITY_COOLDOWN_DAYS));
            // Off the matching index until the cooldown ends.
            donorGeoService.index(donor);

            notificationService.notify(donor.getUserId(), NotificationType.DONATION_RECORDED,
                    "Donation recorded — thank you!",
                    "Your donation of %d unit(s) was recorded. You'll be eligible to donate again on %s."
                            .formatted(dto.units(), donor.getNextEligibleDate()));
        } else {
            BloodBank bank = bloodBankRepository.findById(dto.bloodBankId())
                    .orElseThrow(() -> NotFoundException.of("Blood bank", dto.bloodBankId()));
            BloodInventory inventory = bloodInventoryRepository
                    .findByBloodBankIdAndBloodGroup(bank.getId(), request.getBloodGroup())
                    .filter(inv -> inv.getUnits() >= dto.units())
                    // Guard (spec §3): can't fulfill from a bank without stock
                    .orElseThrow(() -> new ConflictException("Blood bank has insufficient "
                            + request.getBloodGroup().label() + " stock"));
            inventory.setUnits(inventory.getUnits() - dto.units());
            donation.setBloodBank(bank);
        }

        lifecycle.fire(request, RequestEvent.DONATION_RECORDED, userId, null);
        donationRepository.save(donation);
        return RequestResponse.from(request);
    }

    @Transactional
    public RequestResponse cancel(Long userId, Long requestId, String reason) {
        Request request = loadOwnedRequest(userId, requestId);
        lifecycle.fire(request, RequestEvent.CANCEL, userId, reason);

        for (RequestMatch match : requestMatchRepository.findByRequestId(requestId)) {
            if (match.getStatus() == MatchStatus.ACCEPTED || match.getStatus() == MatchStatus.CONFIRMED) {
                notificationService.notify(match.getDonor().getUserId(), NotificationType.REQUEST_CANCELLED,
                        "Request cancelled",
                        request.getHospital().getName() + " cancelled their " + request.getBloodGroup().label()
                                + " request. No donation is needed.");
            }
        }
        return RequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(Long userId, Long requestId) {
        loadOwnedRequest(userId, requestId);
        return historyRepository.findByRequestIdOrderByChangedAtAsc(requestId).stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }

    private Hospital requireHospital(Long userId) {
        return hospitalRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("No hospital profile for this account"));
    }

    private Request loadOwnedRequest(Long userId, Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("Request", requestId));
        if (!request.getHospital().getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your request");
        }
        return request;
    }
}
