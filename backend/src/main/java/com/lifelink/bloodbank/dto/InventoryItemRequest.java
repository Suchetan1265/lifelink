package com.lifelink.bloodbank.dto;

import com.lifelink.common.BloodGroup;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryItemRequest(
        @NotNull BloodGroup bloodGroup,
        @NotNull @Min(0) Integer units) {
}
