package com.lifelink.donor.dto;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull Boolean available) {
}
