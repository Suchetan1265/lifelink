package com.lifelink.auth.dto;

import com.lifelink.common.BloodGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDonorRequest(
        @NotBlank @Email String email,
        String phone,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String fullName,
        @NotNull BloodGroup bloodGroup,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotBlank String city,
        @NotNull @Min(1) @Max(500) Integer radiusKm) {
}
