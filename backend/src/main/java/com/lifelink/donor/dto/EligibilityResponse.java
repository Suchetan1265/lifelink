package com.lifelink.donor.dto;

import java.time.LocalDate;

public record EligibilityResponse(
        boolean eligible,
        LocalDate nextEligibleDate,
        long daysRemaining) {
}
