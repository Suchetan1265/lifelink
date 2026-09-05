package com.lifelink.request.dto;

import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateRequestRequest(
        @NotNull BloodGroup bloodGroup,
        @NotNull @Min(1) @Max(50) Integer units,
        @NotNull Urgency urgency,
        @NotNull @Future Instant neededBy,
        @Size(max = 2000) String notes) {
}
