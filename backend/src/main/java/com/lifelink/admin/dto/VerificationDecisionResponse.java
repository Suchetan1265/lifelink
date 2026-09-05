package com.lifelink.admin.dto;

import com.lifelink.admin.VerificationType;
import com.lifelink.user.UserStatus;

/** Outcome of an approve/reject decision. */
public record VerificationDecisionResponse(
        Long userId,
        VerificationType type,
        String name,
        boolean verified,
        UserStatus status) {
}
