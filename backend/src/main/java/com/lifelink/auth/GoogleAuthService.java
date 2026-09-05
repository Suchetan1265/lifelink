package com.lifelink.auth;

import com.lifelink.auth.dto.TokenResponse;
import com.lifelink.common.BadRequestException;
import com.lifelink.common.ForbiddenException;
import com.lifelink.user.Role;
import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import com.lifelink.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Sign in with Google.
 *
 * <p>The browser obtains an ID token from Google and posts it here; this
 * verifies the signature against Google's published keys and checks the
 * audience and issuer, so a token minted for someone else's application is
 * rejected.
 *
 * <p>A first-time Google user gets a DONOR account with no donor profile yet —
 * blood group, location and travel radius cannot be inferred from a Google
 * identity, so the client collects them immediately afterwards. Organisations
 * cannot register this way at all: they need a licence and admin verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> GOOGLE_ISSUERS =
            List.of("https://accounts.google.com", "accounts.google.com");

    private final UserRepository userRepository;
    private final AuthService authService;
    private final JwtDecoder googleJwtDecoder;

    @Value("${app.google.client-id:}")
    private String clientId;

    @Transactional
    public TokenResponse signIn(String credential) {
        if (clientId == null || clientId.isBlank()) {
            throw new BadRequestException("Google sign-in is not configured on this server");
        }

        Jwt token = verify(credential);
        String email = token.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Google did not supply an email address");
        }
        if (!Boolean.TRUE.equals(token.getClaim("email_verified"))) {
            throw new BadRequestException("This Google account has an unverified email address");
        }

        String normalised = email.toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalised)
                .orElseGet(() -> createDonor(normalised));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ForbiddenException("Account is disabled");
        }
        return authService.issueTokensFor(user);
    }

    private Jwt verify(String credential) {
        Jwt token;
        try {
            token = googleJwtDecoder.decode(credential);
        } catch (JwtException e) {
            log.warn("Rejected a Google credential: {}", e.getMessage());
            throw new BadRequestException("That Google sign-in could not be verified");
        }
        if (!GOOGLE_ISSUERS.contains(String.valueOf(token.getClaims().get("iss")))) {
            throw new BadRequestException("That Google sign-in could not be verified");
        }
        if (!token.getAudience().contains(clientId)) {
            throw new BadRequestException("That Google sign-in was issued for a different application");
        }
        return token;
    }

    /**
     * The account exists but the donor profile does not; the client sends the
     * user straight to a form that fills it in.
     */
    private User createDonor(String email) {
        User user = new User();
        user.setEmail(email);
        // Password login is unavailable for these accounts until a reset is requested.
        user.setPasswordHash(unusablePassword());
        user.setRole(Role.DONOR);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        log.info("Created donor account {} from a Google sign-in", saved.getId());
        return saved;
    }

    /**
     * A random value no password can hash to, rather than null, so every
     * password check simply fails instead of needing a special case.
     */
    private static String unusablePassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "google-only:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
