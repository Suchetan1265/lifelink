package com.lifelink.donor;

import com.lifelink.common.BloodGroup;
import com.lifelink.common.ConflictException;
import com.lifelink.common.ForbiddenException;
import com.lifelink.common.GeoDistance;
import com.lifelink.common.NotFoundException;
import com.lifelink.donor.dto.DonationOpportunityResponse;
import com.lifelink.donor.dto.DonorMatchResponse;
import com.lifelink.hospital.Hospital;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import com.lifelink.request.MatchStatus;
import com.lifelink.request.Request;
import com.lifelink.request.RequestEvent;
import com.lifelink.request.RequestLifecycleService;
import com.lifelink.request.RequestMatch;
import com.lifelink.request.RequestMatchRepository;
import com.lifelink.request.RequestRepository;
import com.lifelink.request.RequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lets a donor browse every open request they could serve and volunteer for one,
 * rather than only seeing what the matching engine pushed at them.
 *
 * <p>The push engine still runs; this is the other half. A donor is held to one
 * live commitment at a time, since they can only give blood once per visit and
 * two hospitals must never both be counting on the same person.
 */
@Service
@RequiredArgsConstructor
public class DonationOpportunityService {

    private static final List<RequestStatus> OPEN = List.of(RequestStatus.RAISED, RequestStatus.MATCHED);

    private final DonorRepository donorRepository;
    private final RequestRepository requestRepository;
    private final RequestMatchRepository requestMatchRepository;
    private final RequestLifecycleService lifecycle;
    private final NotificationService notificationService;

    /**
     * Open requests within the donor's travel radius that their blood group can
     * serve, most urgent first. Deliberately not filtered by their availability
     * toggle: browsing is how someone decides to make themselves available.
     */
    @Transactional(readOnly = true)
    public List<DonationOpportunityResponse> opportunities(Long userId) {
        Donor donor = findDonor(userId);
        Set<BloodGroup> servable = donor.getBloodGroup().canDonateTo();

        Map<Long, RequestMatch> existing = requestMatchRepository
                .findByDonorUserIdOrderByNotifiedAtDesc(userId).stream()
                .collect(Collectors.toMap(
                        match -> match.getRequest().getId(),
                        Function.identity(),
                        (first, second) -> first));

        return requestRepository.findByStatusInWithHospital(OPEN).stream()
                .filter(request -> servable.contains(request.getBloodGroup()))
                .map(request -> Map.entry(request, distanceTo(donor, request)))
                .filter(entry -> entry.getValue() <= donor.getRadiusKm())
                .sorted(Comparator
                        .<Map.Entry<Request, Double>>comparingInt(e -> e.getKey().getUrgency().ordinal())
                        .thenComparingDouble(Map.Entry::getValue))
                .map(entry -> toOpportunity(
                        entry.getKey(), entry.getValue(), existing.containsKey(entry.getKey().getId())))
                .toList();
    }

    /**
     * Volunteers for a request the donor found themselves. This counts as an
     * acceptance, so the hospital sees them alongside donors it notified.
     */
    @Transactional
    public DonorMatchResponse volunteer(Long userId, Long requestId) {
        Donor donor = findDonor(userId);
        requireEligible(donor);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("Request", requestId));
        if (!OPEN.contains(request.getStatus())) {
            throw new ConflictException("This request is no longer open for responses");
        }
        if (!donor.getBloodGroup().canDonateTo().contains(request.getBloodGroup())) {
            throw new ConflictException("Your blood group cannot be given to a "
                    + request.getBloodGroup().label() + " patient");
        }
        if (distanceTo(donor, request) > donor.getRadiusKm()) {
            throw new ConflictException("This request is outside the travel radius on your profile");
        }

        RequestMatch match = requestMatchRepository
                .findByRequestIdAndDonorUserId(requestId, userId)
                .orElseGet(() -> newMatch(donor, request));
        if (match.getStatus() == MatchStatus.ACCEPTED || match.getStatus() == MatchStatus.CONFIRMED) {
            throw new ConflictException("You have already responded to this request");
        }
        // Checked after the per-request cases so the message is the specific one.
        if (requestMatchRepository.existsActiveMatchForDonor(userId) && match.getId() == null) {
            throw new ConflictException(
                    "You are already committed to another request. Finish or decline that one first.");
        }

        match.setStatus(MatchStatus.ACCEPTED);
        match.setRespondedAt(Instant.now());
        requestMatchRepository.save(match);

        if (request.getStatus() == RequestStatus.RAISED) {
            lifecycle.fire(request, RequestEvent.DONOR_ACCEPTED, userId, "Donor volunteered");
        }

        Hospital hospital = request.getHospital();
        notificationService.notify(hospital.getUser().getId(), NotificationType.DONOR_RESPONDED,
                "A donor volunteered for your request",
                "%s (%s) volunteered for request #%d and is willing to come."
                        .formatted(donor.getFullName(), donor.getBloodGroup().label(), request.getId()));

        return DonorMatchResponse.from(match);
    }

    private RequestMatch newMatch(Donor donor, Request request) {
        RequestMatch match = new RequestMatch();
        match.setRequest(request);
        match.setDonor(donor);
        match.setDistanceKm(Math.round(distanceTo(donor, request) * 10) / 10.0);
        return match;
    }

    private static DonationOpportunityResponse toOpportunity(
            Request request, double distanceKm, boolean alreadyResponding) {
        Hospital hospital = request.getHospital();
        return new DonationOpportunityResponse(
                request.getId(),
                hospital.getName(),
                hospital.getAddress(),
                request.getBloodGroup(),
                request.getUnits(),
                request.getUrgency(),
                request.getNeededBy(),
                request.getNotes(),
                Math.round(distanceKm * 10) / 10.0,
                request.getCreatedAt(),
                alreadyResponding);
    }

    private static double distanceTo(Donor donor, Request request) {
        Hospital hospital = request.getHospital();
        return GeoDistance.haversineKm(
                donor.getLat(), donor.getLng(), hospital.getLat(), hospital.getLng());
    }

    private static void requireEligible(Donor donor) {
        LocalDate nextEligible = donor.getNextEligibleDate();
        if (nextEligible != null && nextEligible.isAfter(LocalDate.now())) {
            throw new ForbiddenException("You are not eligible to donate again until " + nextEligible);
        }
    }

    private Donor findDonor(Long userId) {
        return donorRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No donor profile for this account"));
    }
}
