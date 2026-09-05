package com.lifelink.auth.dto;

import com.lifelink.user.Role;
import com.lifelink.user.UserStatus;

public record MeResponse(
        Long id,
        String email,
        String phone,
        Role role,
        UserStatus status,
        String name) {
}
