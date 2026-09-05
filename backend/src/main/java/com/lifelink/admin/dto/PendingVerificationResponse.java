package com.lifelink.admin.dto;

import com.lifelink.admin.VerificationType;
import com.lifelink.bloodbank.BloodBank;
import com.lifelink.hospital.Hospital;
import com.lifelink.user.User;

import java.time.Instant;

/**
 * A registration awaiting admin review. {@code userId} is the id the
 * approve/reject paths take — it identifies the account whichever type it is.
 */
public record PendingVerificationResponse(
        Long userId,
        Long entityId,
        VerificationType type,
        String name,
        String email,
        String phone,
        String address,
        String licenseNo,
        Double lat,
        Double lng,
        Instant registeredAt) {

    public static PendingVerificationResponse from(Hospital hospital) {
        User user = hospital.getUser();
        return new PendingVerificationResponse(
                user.getId(), hospital.getId(), VerificationType.HOSPITAL,
                hospital.getName(), user.getEmail(), user.getPhone(),
                hospital.getAddress(), hospital.getLicenseNo(),
                hospital.getLat(), hospital.getLng(), user.getCreatedAt());
    }

    public static PendingVerificationResponse from(BloodBank bank) {
        User user = bank.getUser();
        return new PendingVerificationResponse(
                user.getId(), bank.getId(), VerificationType.BLOODBANK,
                bank.getName(), user.getEmail(), user.getPhone(),
                bank.getAddress(), null,
                bank.getLat(), bank.getLng(), user.getCreatedAt());
    }
}
