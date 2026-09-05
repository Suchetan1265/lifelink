package com.lifelink.bloodbank.dto;

import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;

import java.time.Instant;

/**
 * An escalated request offered to a blood bank.
 *
 * @param unitsInStock what this bank currently holds of the group, so the UI
 *                     can show whether accepting is actually coverable
 */
public record EscalationResponse(
        Long requestId,
        BloodGroup bloodGroup,
        Integer units,
        Urgency urgency,
        Instant neededBy,
        String notes,
        Instant escalatedAt,
        String hospitalName,
        String hospitalAddress,
        Double distanceKm,
        Integer unitsInStock) {
}
