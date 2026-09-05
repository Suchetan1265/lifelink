package com.lifelink.donor;

import com.lifelink.donor.dto.AvailabilityRequest;
import com.lifelink.donor.dto.DonationResponse;
import com.lifelink.donor.dto.DonorMatchResponse;
import com.lifelink.donor.dto.DonorProfileResponse;
import com.lifelink.donor.dto.EligibilityResponse;
import com.lifelink.donor.dto.UpdateDonorProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/donors/me")
@PreAuthorize("hasRole('DONOR')")
@RequiredArgsConstructor
public class DonorController {

    private final DonorService donorService;

    @GetMapping
    public DonorProfileResponse getProfile(@AuthenticationPrincipal Long userId) {
        return donorService.getProfile(userId);
    }

    @PutMapping
    public DonorProfileResponse updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateDonorProfileRequest request) {
        return donorService.updateProfile(userId, request);
    }

    @PatchMapping("/availability")
    public DonorProfileResponse setAvailability(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AvailabilityRequest request) {
        return donorService.setAvailability(userId, request.available());
    }

    @GetMapping("/eligibility")
    public EligibilityResponse getEligibility(@AuthenticationPrincipal Long userId) {
        return donorService.getEligibility(userId);
    }

    @GetMapping("/matches")
    public List<DonorMatchResponse> getMatches(@AuthenticationPrincipal Long userId) {
        return donorService.getMatches(userId);
    }

    @GetMapping("/donations")
    public List<DonationResponse> getDonations(@AuthenticationPrincipal Long userId) {
        return donorService.getDonations(userId);
    }
}
