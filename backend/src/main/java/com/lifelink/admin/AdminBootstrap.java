package com.lifelink.admin;

import com.lifelink.user.Role;
import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import com.lifelink.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN account on startup when none exists. Without an
 * admin nobody can verify hospitals, so no request could ever be raised.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (isBlank(adminProperties.email()) || isBlank(adminProperties.password())) {
            log.warn("No ADMIN account exists and app.admin.email/password are unset -- "
                    + "hospitals and blood banks cannot be verified. Set ADMIN_EMAIL and ADMIN_PASSWORD.");
            return;
        }

        User admin = new User();
        admin.setEmail(adminProperties.email());
        admin.setPasswordHash(passwordEncoder.encode(adminProperties.password()));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);

        log.info("Created the initial ADMIN account for {}", adminProperties.email());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
