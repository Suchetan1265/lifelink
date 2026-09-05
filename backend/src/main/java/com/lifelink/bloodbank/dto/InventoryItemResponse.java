package com.lifelink.bloodbank.dto;

import com.lifelink.common.BloodGroup;

public record InventoryItemResponse(BloodGroup bloodGroup, Integer units) {
}
