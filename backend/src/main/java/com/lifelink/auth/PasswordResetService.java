package com.lifelink.auth;

import com.lifelink.common.BadRequestException;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import com.lifelink.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Password reset by emailed link.
 *
 * <p>The token is random, stored only as a hash, valid for thirty minutes and
 * usable once. Requesting a reset always reports success whether or not the
 * address exists, so the endpoint cannot be used to discover who has an account.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration LIFETIME = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Value("${app.web.base-url}")
    private String webBaseUrl;

    /** Silent about whether the address is known; the caller always sees success. */
    @Transactional
    public void requestReset(String email) {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            log.info("Password reset requested for an address with no account");
            return;
        }
        User user = found.get();
        if (user.getStatus() == UserStatus.DISABLED) {
            log.info("Password reset requested for disabled account {}", user.getId());
            return;
        }

        // Asking again invalidates the previous link.
        tokenRepository.invalidateOutstanding(user.getId(), Instant.now());

        String token = randomToken();
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setTokenHash(sha256(token));
        entity.setExpiresAt(Instant.now().plus(LIFETIME));
        tokenRepository.save(entity);

        String link = webBaseUrl + "/reset-password?token=" + token;
        notificationService.notify(user.getId(), NotificationType.PASSWORD_RESET,
                "Choose a new password",
                "Open this link within 30 minutes to set a new password: " + link
                        + "\n\nIf you did not ask for this, ignore it and nothing changes.");
    }

    /** Consumes the token, sets the password and ends every existing session. */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken stored = tokenRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> new BadRequestException("This reset link is not valid"));

        if (stored.getUsedAt() != null) {
            throw new BadRequestException("This reset link has already been used");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This reset link has expired; request a new one");
        }

        User user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        stored.setUsedAt(Instant.now());

        // Whoever prompted the reset may have had the old password.
        refreshTokenRepository.revokeAllForUser(user.getId());

        log.info("Password reset completed for user {}", user.getId());
    }

    private static String randomToken() {
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
