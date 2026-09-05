package com.lifelink.auth;

import com.lifelink.auth.dto.LoginRequest;
import com.lifelink.auth.dto.MeResponse;
import com.lifelink.auth.dto.RegisterBloodBankRequest;
import com.lifelink.auth.dto.RegisterDonorRequest;
import com.lifelink.auth.dto.RegisterHospitalRequest;
import com.lifelink.auth.dto.TokenResponse;
import com.lifelink.bloodbank.BloodBank;
import com.lifelink.bloodbank.BloodBankRepository;
import com.lifelink.common.ConflictException;
import com.lifelink.common.ForbiddenException;
import com.lifelink.common.NotFoundException;
import com.lifelink.common.UnauthorizedException;
import com.lifelink.donor.Donor;
import com.lifelink.donor.DonorRepository;
import com.lifelink.hospital.Hospital;
import com.lifelink.hospital.HospitalRepository;
import com.lifelink.security.JwtProperties;
import com.lifelink.security.JwtService;
import com.lifelink.user.Role;
import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import com.lifelink.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final DonorRepository donorRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bloodBankRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse registerDonor(RegisterDonorRequest request) {
        User user = createUser(request.email(), request.phone(), request.password(), Role.DONOR, UserStatus.ACTIVE);

        Donor donor = new Donor();
        donor.setUser(user);
        donor.setFullName(request.fullName());
        donor.setBloodGroup(request.bloodGroup());
        donor.setLat(request.lat());
        donor.setLng(request.lng());
        donor.setCity(request.city());
        donor.setRadiusKm(request.radiusKm());
        donor.setAvailable(false);
        donorRepository.save(donor);

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse registerHospital(RegisterHospitalRequest request) {
        // PENDING until an ADMIN verifies the license (spec §2.B.1)
        User user = createUser(request.email(), request.phone(), request.password(), Role.HOSPITAL, UserStatus.PENDING);

        Hospital hospital = new Hospital();
        hospital.setUser(user);
        hospital.setName(request.name());
        hospital.setLicenseNo(request.licenseNo());
        hospital.setAddress(request.address());
        hospital.setLat(request.lat());
        hospital.setLng(request.lng());
        hospital.setVerified(false);
        hospitalRepository.save(hospital);

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse registerBloodBank(RegisterBloodBankRequest request) {
        User user = createUser(request.email(), request.phone(), request.password(), Role.BLOOD_BANK, UserStatus.PENDING);

        BloodBank bank = new BloodBank();
        bank.setUser(user);
        bank.setName(request.name());
        bank.setAddress(request.address());
        bank.setLat(request.lat());
        bank.setLng(request.lng());
        bank.setVerified(false);
        bloodBankRepository.save(bank);

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        requireNotDisabled(user);
        return issueTokens(user);
    }

    /** Rotates the refresh token: the presented one is revoked and a new pair is issued. */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }
        User user = stored.getUser();
        requireNotDisabled(user);
        stored.setRevoked(true);
        return issueTokens(user);
    }

    /** Idempotent: revokes the token if it exists, succeeds either way. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
        String name = switch (user.getRole()) {
            case DONOR -> donorRepository.findById(userId).map(Donor::getFullName).orElse(null);
            case HOSPITAL -> hospitalRepository.findByUserId(userId).map(Hospital::getName).orElse(null);
            case BLOOD_BANK -> bloodBankRepository.findByUserId(userId).map(BloodBank::getName).orElse(null);
            case ADMIN -> null;
        };
        // A Google sign-in creates the account before the donor details exist.
        boolean profileComplete = user.getRole() != Role.DONOR || name != null;
        return new MeResponse(user.getId(), user.getEmail(), user.getPhone(),
                user.getRole(), user.getStatus(), name, profileComplete);
    }

    /** Issues a session for an already-authenticated user (see GoogleAuthService). */
    @Transactional
    public TokenResponse issueTokensFor(User user) {
        return issueTokens(user);
    }

    private User createUser(String email, String phone, String rawPassword, Role role, UserStatus status) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(status);
        return userRepository.save(user);
    }

    private void requireNotDisabled(User user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ForbiddenException("Account is disabled");
        }
    }

    private TokenResponse issueTokens(User user) {
        String refreshToken = generateRefreshToken();

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256(refreshToken));
        entity.setExpiresAt(Instant.now().plus(jwtProperties.refreshTtlDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(entity);

        return new TokenResponse(jwtService.generateAccessToken(user), refreshToken);
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
