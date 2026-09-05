package com.lifelink.request;

import com.lifelink.bloodbank.BloodBank;
import com.lifelink.bloodbank.BloodBankRepository;
import com.lifelink.bloodbank.BloodBankService;
import com.lifelink.common.GeoDistance;
import com.lifelink.hospital.Hospital;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The scheduled half of the request lifecycle (spec §2.E.2 and §2.E.3): moving
 * stalled requests to ESCALATED and past-due ones to EXPIRED.
 *
 * <p>Deadlines are applied in Java rather than SQL so {@link Urgency} stays the
 * single place the per-urgency windows are defined. Only non-terminal requests
 * are loaded, so the working set stays small.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestMaintenanceService {

    private final RequestRepository requestRepository;
    private final BloodBankRepository bloodBankRepository;
    private final RequestLifecycleService lifecycle;
    private final NotificationService notificationService;

    /** @return how many requests were escalated */
    @Transactional
    public int escalateOverdueRequests() {
        Instant now = Instant.now();
        List<Request> overdue = requestRepository
                .findByStatusInWithHospital(List.of(RequestStatus.RAISED, RequestStatus.MATCHED)).stream()
                .filter(request -> request.getCreatedAt().plus(request.getUrgency().escalationAfter()).isBefore(now))
                .toList();
        if (overdue.isEmpty()) {
            return 0;
        }

        List<BloodBank> banks = bloodBankRepository.findByVerifiedTrue();
        for (Request request : overdue) {
            lifecycle.fire(request, RequestEvent.ESCALATE_TIMEOUT, null,
                    "No donor confirmed within the " + request.getUrgency() + " window");
            alertBanksInReach(request, banks);
        }
        log.info("Escalated {} request(s) past their urgency deadline", overdue.size());
        return overdue.size();
    }

    /** @return how many requests expired */
    @Transactional
    public int expirePastDueRequests() {
        Instant now = Instant.now();
        List<Request> pastDue = requestRepository
                .findByStatusInWithHospital(
                        List.of(RequestStatus.RAISED, RequestStatus.MATCHED, RequestStatus.ESCALATED)).stream()
                .filter(request -> !request.getNeededBy().isAfter(now))
                .toList();

        for (Request request : pastDue) {
            lifecycle.fire(request, RequestEvent.NEEDED_BY_PASSED, null, "Needed-by time passed");
            notificationService.notify(request.getHospital().getUser().getId(),
                    NotificationType.REQUEST_EXPIRED,
                    "Request expired without a donation",
                    "Your %s request for %d unit(s) passed its needed-by time of %s."
                            .formatted(request.getBloodGroup().label(), request.getUnits(), request.getNeededBy()));
        }
        if (!pastDue.isEmpty()) {
            log.info("Expired {} request(s) past their needed-by time", pastDue.size());
        }
        return pastDue.size();
    }

    private void alertBanksInReach(Request request, List<BloodBank> banks) {
        Hospital hospital = request.getHospital();
        for (BloodBank bank : banks) {
            double distanceKm = GeoDistance.haversineKm(
                    bank.getLat(), bank.getLng(), hospital.getLat(), hospital.getLng());
            if (distanceKm > BloodBankService.ESCALATION_RADIUS_KM) {
                continue;
            }
            notificationService.notify(bank.getUser().getId(), NotificationType.REQUEST_ESCALATED,
                    "Escalated: %s needs %s".formatted(hospital.getName(), request.getBloodGroup().label()),
                    "%d unit(s) needed by %s, about %.1f km away. No donor confirmed in time."
                            .formatted(request.getUnits(), request.getNeededBy(), distanceKm));
        }
    }
}
