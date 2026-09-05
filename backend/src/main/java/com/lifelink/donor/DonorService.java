package com.lifelink.donor;

import com.lifelink.common.NotFoundException;
import com.lifelink.donation.DonationRepository;
import com.lifelink.donor.dto.DonationResponse;
import com.lifelink.donor.dto.DonorMatchResponse;
import com.lifelink.donor.dto.DonorProfileResponse;
import com.lifelink.donor.dto.EligibilityResponse;
import com.lifelink.donor.dto.UpdateDonorProfileRequest;
import com.lifelink.request.RequestMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DonorService {

    private final DonorRepository donorRepository;
    private final RequestMatchRepository requestMatchRepository;
    private final DonationRepository donationRepository;

    @Transactional(readOnly = true)
    public DonorProfileResponse getProfile(Long userId) {
        return DonorProfileResponse.from(findDonor(userId));
    }

    @Transactional
    public DonorProfileResponse updateProfile(Long userId, UpdateDonorProfileRequest request) {
        Donor donor = findDonor(userId);
        donor.setFullName(request.fullName());
        donor.setBloodGroup(request.bloodGroup());
        donor.setLat(request.lat());
        donor.setLng(request.lng());
        donor.setCity(request.city());
        donor.setRadiusKm(request.radiusKm());
        return DonorProfileResponse.from(donor);
    }

    /** Redis GEO write-through (spec §7) is added with the week-3 infra pass. */
    @Transactional
    public DonorProfileResponse setAvailability(Long userId, boolean available) {
        Donor donor = findDonor(userId);
        donor.setAvailable(available);
        return DonorProfileResponse.from(donor);
    }

    @Transactional(readOnly = true)
    public EligibilityResponse getEligibility(Long userId) {
        Donor donor = findDonor(userId);
        LocalDate nextEligible = donor.getNextEligibleDate();
        LocalDate today = LocalDate.now();
        boolean eligible = nextEligible == null || !nextEligible.isAfter(today);
        long daysRemaining = eligible ? 0 : ChronoUnit.DAYS.between(today, nextEligible);
        return new EligibilityResponse(eligible, nextEligible, daysRemaining);
    }

    @Transactional(readOnly = true)
    public List<DonorMatchResponse> getMatches(Long userId) {
        return requestMatchRepository.findByDonorUserIdOrderByNotifiedAtDesc(userId).stream()
                .map(DonorMatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> getDonations(Long userId) {
        return donationRepository.findByDonorUserIdOrderByDonatedAtDesc(userId).stream()
                .map(DonationResponse::from)
                .toList();
    }

    private Donor findDonor(Long userId) {
        return donorRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No donor profile for this account"));
    }
}
