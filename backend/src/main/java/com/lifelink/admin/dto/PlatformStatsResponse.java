package com.lifelink.admin.dto;

import java.util.Map;

/**
 * Platform metrics for the admin dashboard (spec §2.D.2). Picks up a 5-minute
 * Redis cache when the cache layer lands (spec §7, key {@code stats:admin}).
 *
 * @param fulfillmentRate  fulfilled requests over all requests raised, 0–1
 * @param avgHoursToFulfill null until at least one request is fulfilled
 */
public record PlatformStatsResponse(
        long totalRequests,
        Map<String, Long> requestsByStatus,
        double fulfillmentRate,
        Double avgHoursToFulfill,
        long totalDonors,
        long availableDonors,
        Map<String, Long> donorsByBloodGroup,
        Map<String, Long> donorsByCity,
        long verifiedHospitals,
        long verifiedBloodBanks,
        long pendingVerifications) {
}
