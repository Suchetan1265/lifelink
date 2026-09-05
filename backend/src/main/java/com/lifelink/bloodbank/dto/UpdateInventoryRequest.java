package com.lifelink.bloodbank.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Upserts the listed blood groups; groups left out keep their current units. */
public record UpdateInventoryRequest(@NotEmpty @Valid List<InventoryItemRequest> items) {
}
