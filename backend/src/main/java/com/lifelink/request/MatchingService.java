package com.lifelink.request;

import com.lifelink.common.BloodGroup;
import com.lifelink.donor.DonorRepository;
import com.lifelink.hospital.Hospital;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import com.lifelink.redis.DonorGeoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matching engine (spec §2.E.1): on request creation, find the nearest
 * compatible, available, eligible donors and notify the top N.
 *
 * <p>The geo search runs in Redis (spec §7) and the result is then narrowed in
 * Postgres, which is the only place that knows whether a donor is already tied
 * to an active request. If Redis is unreachable or holds nothing, the whole
 * match falls back to the plain-SQL Haversine query.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    static final int MAX_NOTIFIED_DONORS = 20;

    /**
     * How far the GEO search looks before each donor's own travel radius is
     * applied. Matches the ceiling on {@code radiusKm} at registration.
     */
    static final double MAX_SEARCH_RADIUS_KM = 500;

    private final DonorRepository donorRepository;
    private final RequestMatchRepository requestMatchRepository;
    private final NotificationService notificationService;
    private final DonorGeoService donorGeoService;

    /** @return number of donors matched and notified */
    @Transactional(propagation = Propagation.MANDATORY)
    public int matchDonors(Request request) {
        Hospital hospital = request.getHospital();
        Set<BloodGroup> compatibleGroups = request.getBloodGroup().compatibleDonors();

        List<Candidate> candidates = searchWithRedis(hospital, compatibleGroups);
        if (candidates == null) {
            candidates = searchWithSql(hospital, compatibleGroups);
        }

        for (Candidate candidate : candidates) {
            RequestMatch match = new RequestMatch();
            match.setRequest(request);
            match.setDonor(donorRepository.getReferenceById(candidate.donorId()));
            match.setStatus(MatchStatus.NOTIFIED);
            match.setDistanceKm(candidate.distanceKm());
            requestMatchRepository.save(match);

            notificationService.notify(candidate.donorId(), NotificationType.MATCH_FOUND,
                    hospital.getName() + " needs " + request.getBloodGroup().label() + " blood",
                    "%d unit(s) needed by %s, urgency %s, about %.1f km from you. Open your matches to respond."
                            .formatted(request.getUnits(), request.getNeededBy(),
                                    request.getUrgency(), candidate.distanceKm()));
        }
        return candidates.size();
    }

    /**
     * @return ranked candidates, or null when Redis cannot answer and the SQL
     *         matcher should take over
     */
    private List<Candidate> searchWithRedis(Hospital hospital, Set<BloodGroup> compatibleGroups) {
        Map<Long, Double> hits = donorGeoService.search(
                hospital.getLat(), hospital.getLng(), MAX_SEARCH_RADIUS_KM, compatibleGroups);

        // An empty index is indistinguishable from a flushed one, so let SQL decide.
        if (hits == null || hits.isEmpty()) {
            return null;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Object[] row : donorRepository.findMatchableAmong(hits.keySet())) {
            long donorId = ((Number) row[0]).longValue();
            int travelRadiusKm = ((Number) row[1]).intValue();
            double distanceKm = hits.get(donorId);
            if (distanceKm <= travelRadiusKm) {
                candidates.add(new Candidate(donorId, round(distanceKm)));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distanceKm));
        return candidates.size() > MAX_NOTIFIED_DONORS
                ? candidates.subList(0, MAX_NOTIFIED_DONORS)
                : candidates;
    }

    private List<Candidate> searchWithSql(Hospital hospital, Set<BloodGroup> compatibleGroups) {
        List<String> groupNames = compatibleGroups.stream().map(Enum::name).toList();
        List<Object[]> rows = donorRepository.findMatchCandidates(
                hospital.getLat(), hospital.getLng(), groupNames, MAX_NOTIFIED_DONORS);

        List<Candidate> candidates = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            candidates.add(new Candidate(
                    ((Number) row[0]).longValue(),
                    round(((Number) row[1]).doubleValue())));
        }
        return candidates;
    }

    private static double round(double distanceKm) {
        return Math.round(distanceKm * 10) / 10.0;
    }

    private record Candidate(Long donorId, double distanceKm) {
    }
}
