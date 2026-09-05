package com.lifelink.bloodbank;

import com.lifelink.bloodbank.dto.EscalationResponse;
import com.lifelink.bloodbank.dto.InventoryItemResponse;
import com.lifelink.bloodbank.dto.UpdateInventoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bloodbanks/me")
@PreAuthorize("hasRole('BLOOD_BANK')")
@RequiredArgsConstructor
public class BloodBankController {

    private final BloodBankService bloodBankService;

    @GetMapping("/inventory")
    public List<InventoryItemResponse> inventory(@AuthenticationPrincipal Long userId) {
        return bloodBankService.inventory(userId);
    }

    @PutMapping("/inventory")
    public List<InventoryItemResponse> updateInventory(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateInventoryRequest request) {
        return bloodBankService.updateInventory(userId, request);
    }

    @GetMapping("/escalations")
    public List<EscalationResponse> escalations(@AuthenticationPrincipal Long userId) {
        return bloodBankService.escalations(userId);
    }
}
