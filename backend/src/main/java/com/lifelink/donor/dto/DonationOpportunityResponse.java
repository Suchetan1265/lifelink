package com.lifelink.donor.dto;

import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;

import java.time.Instant;

/**
 * An open request this donor could serve, for the browse view (spec §2.A.4).
 *
 * <p>Unlike a match, nobody has picked this donor for it — they are looking at
 * everything within reach and choosing.
 *
 * @param alreadyResponding true when they have already been matched to it, so
 *                          the UI points them at their matches instead
 */
public record DonationOpportunityResponse(
        Long requestId,
        String hospitalName,
        String hospitalAddress,
        BloodGroup bloodGroup,
        Integer units,
        Urgency urgency,
        Instant neededBy,
        String notes,
        Double distanceKm,
        Instant raisedAt,
        boolean alreadyResponding) {
}
