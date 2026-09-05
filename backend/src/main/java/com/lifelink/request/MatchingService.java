package com.lifelink.request;

import com.lifelink.common.BloodGroup;
import com.lifelink.common.GeoDistance;
import com.lifelink.donor.Donor;
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
import java.util.Optional;
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

    private final RequestRepository requestRepository;
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
     * Attaches a donor who has just become matchable to the best open request
     * they can serve (spec §2.A.4).
     *
     * <p>Matching otherwise only runs when a request is raised, which leaves a
     * donor who turns availability on a minute later hearing nothing about a
     * request that is still open and needs exactly their blood group. Only one
     * request is taken, matching the rule that a donor is never tied to two
     * active requests at once.
     *
     * @return the request they were matched to, if any
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Request> matchToOpenRequest(Donor donor) {
        if (requestMatchRepository.existsActiveMatchForDonor(donor.getUserId())) {
            return Optional.empty();
        }
        Set<BloodGroup> servable = donor.getBloodGroup().canDonateTo();

        Optional<Request> best = requestRepository
                .findByStatusInWithHospital(List.of(RequestStatus.RAISED, RequestStatus.MATCHED)).stream()
                .filter(request -> servable.contains(request.getBloodGroup()))
                .filter(request -> distanceTo(donor, request) <= donor.getRadiusKm())
                .min(Comparator
                        .<Request>comparingInt(request -> request.getUrgency().ordinal())
                        .thenComparingDouble(request -> distanceTo(donor, request)));

        best.ifPresent(request -> {
            double distanceKm = round(distanceTo(donor, request));
            RequestMatch match = new RequestMatch();
            match.setRequest(request);
            match.setDonor(donor);
            match.setStatus(MatchStatus.NOTIFIED);
            match.setDistanceKm(distanceKm);
            requestMatchRepository.save(match);

            Hospital hospital = request.getHospital();
            notificationService.notify(donor.getUserId(), NotificationType.MATCH_FOUND,
                    hospital.getName() + " needs " + request.getBloodGroup().label() + " blood",
                    "%d unit(s) needed by %s, urgency %s, about %.1f km from you. Open your matches to respond."
                            .formatted(request.getUnits(), request.getNeededBy(),
                                    request.getUrgency(), distanceKm));
            log.info("Matched newly available donor {} to open request {}", donor.getUserId(), request.getId());
        });
        return best;
    }

    private static double distanceTo(Donor donor, Request request) {
        Hospital hospital = request.getHospital();
        return GeoDistance.haversineKm(
                donor.getLat(), donor.getLng(), hospital.getLat(), hospital.getLng());
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
