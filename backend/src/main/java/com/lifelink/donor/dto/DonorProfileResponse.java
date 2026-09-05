package com.lifelink.donor.dto;

import com.lifelink.common.BloodGroup;
import com.lifelink.donor.Donor;

import java.time.LocalDate;

public record DonorProfileResponse(
        Long userId,
        String fullName,
        BloodGroup bloodGroup,
        Double lat,
        Double lng,
        String city,
        Integer radiusKm,
        boolean available,
        LocalDate lastDonationDate,
        LocalDate nextEligibleDate) {

    public static DonorProfileResponse from(Donor donor) {
        return new DonorProfileResponse(
                donor.getUserId(),
                donor.getFullName(),
                donor.getBloodGroup(),
                donor.getLat(),
                donor.getLng(),
                donor.getCity(),
                donor.getRadiusKm(),
                donor.isAvailable(),
                donor.getLastDonationDate(),
                donor.getNextEligibleDate());
    }
}
