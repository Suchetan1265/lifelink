package com.lifelink.bloodbank.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FulfillEscalationRequest(@NotNull @Min(1) Integer units) {
}
