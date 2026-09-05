package com.lifelink.auth;

import com.lifelink.auth.dto.LoginRequest;
import com.lifelink.auth.dto.MeResponse;
import com.lifelink.auth.dto.RefreshRequest;
import com.lifelink.auth.dto.RegisterBloodBankRequest;
import com.lifelink.auth.dto.RegisterDonorRequest;
import com.lifelink.auth.dto.RegisterHospitalRequest;
import com.lifelink.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/donor")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse registerDonor(@Valid @RequestBody RegisterDonorRequest request) {
        return authService.registerDonor(request);
    }

    @PostMapping("/register/hospital")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse registerHospital(@Valid @RequestBody RegisterHospitalRequest request) {
        return authService.registerHospital(request);
    }

    @PostMapping("/register/bloodbank")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse registerBloodBank(@Valid @RequestBody RegisterBloodBankRequest request) {
        return authService.registerBloodBank(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return authService.me(userId);
    }
}
