package com.lifelink.admin;

import com.lifelink.admin.dto.PendingVerificationResponse;
import com.lifelink.admin.dto.PlatformStatsResponse;
import com.lifelink.admin.dto.RejectVerificationRequest;
import com.lifelink.admin.dto.UpdateUserStatusRequest;
import com.lifelink.admin.dto.UserSummaryResponse;
import com.lifelink.admin.dto.VerificationDecisionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/verifications")
    public List<PendingVerificationResponse> pendingVerifications(@RequestParam String type) {
        return adminService.pendingVerifications(VerificationType.from(type));
    }

    @PostMapping("/verifications/{userId}/approve")
    public VerificationDecisionResponse approve(@PathVariable Long userId) {
        return adminService.approve(userId);
    }

    @PostMapping("/verifications/{userId}/reject")
    public VerificationDecisionResponse reject(
            @PathVariable Long userId,
            @Valid @RequestBody(required = false) RejectVerificationRequest request) {
        return adminService.reject(userId, request == null ? null : request.reason());
    }

    @GetMapping("/stats")
    public PlatformStatsResponse stats() {
        return adminService.stats();
    }

    @PatchMapping("/users/{userId}/status")
    public UserSummaryResponse updateUserStatus(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminService.updateUserStatus(adminUserId, userId, request.status());
    }
}
