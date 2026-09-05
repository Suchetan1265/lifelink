package com.lifelink.request;

import com.lifelink.donor.DonorRepository;
import com.lifelink.hospital.Hospital;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Matching engine (spec §2.E.1): on request creation, find the nearest
 * compatible, available, eligible donors and notify the top N.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    static final int MAX_NOTIFIED_DONORS = 20;

    private final DonorRepository donorRepository;
    private final RequestMatchRepository requestMatchRepository;
    private final NotificationService notificationService;

    /** @return number of donors matched and notified */
    @Transactional(propagation = Propagation.MANDATORY)
    public int matchDonors(Request request) {
        Hospital hospital = request.getHospital();
        List<String> compatibleGroups = request.getBloodGroup().compatibleDonors().stream()
                .map(Enum::name)
                .toList();

        List<Object[]> candidates = donorRepository.findMatchCandidates(
                hospital.getLat(), hospital.getLng(), compatibleGroups, MAX_NOTIFIED_DONORS);

        for (Object[] row : candidates) {
            long donorId = ((Number) row[0]).longValue();
            double distanceKm = Math.round(((Number) row[1]).doubleValue() * 10) / 10.0;

            RequestMatch match = new RequestMatch();
            match.setRequest(request);
            match.setDonor(donorRepository.getReferenceById(donorId));
            match.setStatus(MatchStatus.NOTIFIED);
            match.setDistanceKm(distanceKm);
            requestMatchRepository.save(match);

            notificationService.notify(donorId, NotificationType.MATCH_FOUND,
                    hospital.getName() + " needs " + request.getBloodGroup().label() + " blood",
                    "%d unit(s) needed by %s, urgency %s, about %.1f km from you. Open your matches to respond."
                            .formatted(request.getUnits(), request.getNeededBy(), request.getUrgency(), distanceKm));
        }
        return candidates.size();
    }
}
