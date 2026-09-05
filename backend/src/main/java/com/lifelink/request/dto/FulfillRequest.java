package com.lifelink.request.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Exactly one of donorId / bloodBankId must be set (checked in the service). */
public record FulfillRequest(
        Long donorId,
        Long bloodBankId,
        @NotNull @Min(1) Integer units) {
}
