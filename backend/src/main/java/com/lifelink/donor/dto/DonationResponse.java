package com.lifelink.donor.dto;

import com.lifelink.donation.Donation;

import java.time.Instant;

public record DonationResponse(
        Long id,
        Long requestId,
        String hospitalName,
        Integer units,
        Instant donatedAt) {

    public static DonationResponse from(Donation donation) {
        return new DonationResponse(
                donation.getId(),
                donation.getRequest().getId(),
                donation.getRequest().getHospital().getName(),
                donation.getUnits(),
                donation.getDonatedAt());
    }
}
