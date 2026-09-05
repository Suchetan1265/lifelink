package com.lifelink.bloodbank;

import com.lifelink.bloodbank.dto.EscalationResponse;
import com.lifelink.bloodbank.dto.InventoryItemRequest;
import com.lifelink.bloodbank.dto.InventoryItemResponse;
import com.lifelink.bloodbank.dto.UpdateInventoryRequest;
import com.lifelink.common.BloodGroup;
import com.lifelink.common.ConflictException;
import com.lifelink.common.ForbiddenException;
import com.lifelink.common.GeoDistance;
import com.lifelink.common.NotFoundException;
import com.lifelink.donation.Donation;
import com.lifelink.donation.DonationRepository;
import com.lifelink.hospital.Hospital;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import com.lifelink.request.Request;
import com.lifelink.request.RequestEvent;
import com.lifelink.request.RequestLifecycleService;
import com.lifelink.request.RequestRepository;
import com.lifelink.request.RequestStatus;
import com.lifelink.request.dto.RequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Blood bank journey (spec §2.C): stock inventory, and the escalation queue a
 * request falls into when no donor confirms before its urgency deadline.
 */
@Service
@RequiredArgsConstructor
public class BloodBankService {

    /**
     * Blood banks have no per-bank radius column, so escalations reach every
     * bank within this distance of the requesting hospital.
     */
    public static final int ESCALATION_RADIUS_KM = 50;

    private final BloodBankRepository bloodBankRepository;
    private final BloodInventoryRepository inventoryRepository;
    private final RequestRepository requestRepository;
    private final DonationRepository donationRepository;
    private final RequestLifecycleService lifecycle;
    private final NotificationService notificationService;

    /** Always returns all eight groups so the inventory editor has a full grid. */
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> inventory(Long userId) {
        BloodBank bank = requireVerifiedBank(userId);
        Map<BloodGroup, Integer> held = stockByGroup(bank.getId());
        List<InventoryItemResponse> items = new ArrayList<>();
        for (BloodGroup group : BloodGroup.values()) {
            items.add(new InventoryItemResponse(group, held.getOrDefault(group, 0)));
        }
        return items;
    }

    @Transactional
    public List<InventoryItemResponse> updateInventory(Long userId, UpdateInventoryRequest dto) {
        BloodBank bank = requireVerifiedBank(userId);

        for (InventoryItemRequest item : dto.items()) {
            BloodInventory row = inventoryRepository
                    .findByBloodBankIdAndBloodGroup(bank.getId(), item.bloodGroup())
                    .orElseGet(() -> {
                        BloodInventory created = new BloodInventory();
                        created.setBloodBank(bank);
                        created.setBloodGroup(item.bloodGroup());
                        return created;
                    });
            row.setUnits(item.units());
            inventoryRepository.save(row);
        }
        return inventory(userId);
    }

    @Transactional(readOnly = true)
    public List<EscalationResponse> escalations(Long userId) {
        BloodBank bank = requireVerifiedBank(userId);
        Map<BloodGroup, Integer> stock = stockByGroup(bank.getId());

        return requestRepository.findByStatusInWithHospital(List.of(RequestStatus.ESCALATED)).stream()
                .map(request -> Map.entry(request, distanceFrom(bank, request)))
                .filter(entry -> entry.getValue() <= ESCALATION_RADIUS_KM)
                .sorted(Comparator
                        .<Map.Entry<Request, Double>>comparingInt(e -> e.getKey().getUrgency().ordinal())
                        .thenComparing(e -> e.getKey().getNeededBy()))
                .map(entry -> toEscalation(entry.getKey(), entry.getValue(), stock))
                .toList();
    }

    @Transactional
    public RequestResponse acceptEscalation(Long userId, Long requestId) {
        BloodBank bank = requireVerifiedBank(userId);
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("Request", requestId));
        requireWithinReach(bank, request);

        // Fires from ESCALATED only, so a second bank accepting gets a 409.
        lifecycle.fire(request, RequestEvent.BANK_ACCEPTED, userId, "Accepted by " + bank.getName());
        request.setAcceptedBank(bank);

        notificationService.notify(request.getHospital().getUser().getId(), NotificationType.BANK_ACCEPTED,
                bank.getName() + " is covering your request",
                "%s accepted your escalated %s request for %d unit(s). Contact them at %s."
                        .formatted(bank.getName(), request.getBloodGroup().label(),
                                request.getUnits(), bank.getAddress()));

        return RequestResponse.from(request);
    }

    @Transactional
    public RequestResponse fulfillEscalation(Long userId, Long requestId, int units) {
        BloodBank bank = requireVerifiedBank(userId);
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> NotFoundException.of("Request", requestId));

        if (request.getAcceptedBank() == null || !request.getAcceptedBank().getId().equals(bank.getId())) {
            throw new ForbiddenException("This request was not accepted by your blood bank");
        }

        // Guard (spec §3): cannot fulfill from a bank without stock.
        BloodInventory stock = inventoryRepository
                .findByBloodBankIdAndBloodGroup(bank.getId(), request.getBloodGroup())
                .filter(row -> row.getUnits() >= units)
                .orElseThrow(() -> new ConflictException(
                        "Insufficient " + request.getBloodGroup().label() + " stock"));
        stock.setUnits(stock.getUnits() - units);

        lifecycle.fire(request, RequestEvent.DONATION_RECORDED, userId, null);

        Donation donation = new Donation();
        donation.setRequest(request);
        donation.setBloodBank(bank);
        donation.setUnits(units);
        donationRepository.save(donation);

        notificationService.notify(request.getHospital().getUser().getId(), NotificationType.DONATION_RECORDED,
                "Request fulfilled by " + bank.getName(),
                "%d unit(s) of %s were released from %s."
                        .formatted(units, request.getBloodGroup().label(), bank.getName()));

        return RequestResponse.from(request);
    }

    private Map<BloodGroup, Integer> stockByGroup(Long bankId) {
        Map<BloodGroup, Integer> stock = new EnumMap<>(BloodGroup.class);
        for (BloodInventory row : inventoryRepository.findByBloodBankId(bankId)) {
            stock.put(row.getBloodGroup(), row.getUnits());
        }
        return stock;
    }

    private static EscalationResponse toEscalation(
            Request request, double distanceKm, Map<BloodGroup, Integer> stock) {
        Hospital hospital = request.getHospital();
        return new EscalationResponse(
                request.getId(),
                request.getBloodGroup(),
                request.getUnits(),
                request.getUrgency(),
                request.getNeededBy(),
                request.getNotes(),
                request.getEscalatedAt(),
                hospital.getName(),
                hospital.getAddress(),
                Math.round(distanceKm * 10) / 10.0,
                stock.getOrDefault(request.getBloodGroup(), 0));
    }

    private static double distanceFrom(BloodBank bank, Request request) {
        Hospital hospital = request.getHospital();
        return GeoDistance.haversineKm(
                bank.getLat(), bank.getLng(), hospital.getLat(), hospital.getLng());
    }

    private void requireWithinReach(BloodBank bank, Request request) {
        if (distanceFrom(bank, request) > ESCALATION_RADIUS_KM) {
            throw new ForbiddenException("This request is outside your service radius");
        }
    }

    private BloodBank requireVerifiedBank(Long userId) {
        BloodBank bank = bloodBankRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("No blood bank profile for this account"));
        if (!bank.isVerified()) {
            throw new ForbiddenException("Blood bank is pending admin verification");
        }
        return bank;
    }
}
