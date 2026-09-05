package com.lifelink.admin.dto;

import com.lifelink.user.Role;
import com.lifelink.user.User;
import com.lifelink.user.UserStatus;

public record UserSummaryResponse(Long id, String email, String phone, Role role, UserStatus status) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(), user.getEmail(), user.getPhone(), user.getRole(), user.getStatus());
    }
}
