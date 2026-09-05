package com.lifelink.bloodbank;

import com.lifelink.bloodbank.dto.FulfillEscalationRequest;
import com.lifelink.request.dto.RequestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/escalations")
@PreAuthorize("hasRole('BLOOD_BANK')")
@RequiredArgsConstructor
public class EscalationController {

    private final BloodBankService bloodBankService;

    @PostMapping("/{requestId}/accept")
    public RequestResponse accept(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestId) {
        return bloodBankService.acceptEscalation(userId, requestId);
    }

    @PostMapping("/{requestId}/fulfill")
    public RequestResponse fulfill(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestId,
            @Valid @RequestBody FulfillEscalationRequest request) {
        return bloodBankService.fulfillEscalation(userId, requestId, request.units());
    }
}
