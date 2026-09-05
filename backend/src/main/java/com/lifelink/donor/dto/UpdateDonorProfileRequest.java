package com.lifelink.donor.dto;

import com.lifelink.common.BloodGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDonorProfileRequest(
        @NotBlank String fullName,
        @NotNull BloodGroup bloodGroup,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotBlank String city,
        @NotNull @Min(1) @Max(500) Integer radiusKm) {
}
