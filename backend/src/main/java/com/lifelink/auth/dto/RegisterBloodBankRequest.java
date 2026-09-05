package com.lifelink.auth.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterBloodBankRequest(
        @NotBlank @Email String email,
        String phone,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String name,
        @NotBlank String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
}
