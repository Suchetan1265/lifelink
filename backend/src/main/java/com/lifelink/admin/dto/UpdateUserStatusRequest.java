package com.lifelink.admin.dto;

import com.lifelink.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
