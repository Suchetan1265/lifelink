package com.lifelink.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRequest(@NotBlank @Size(max = 512) String reason) {
}
