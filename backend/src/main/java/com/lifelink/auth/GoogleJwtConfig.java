package com.lifelink.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies Google ID tokens against Google's published signing keys. The key
 * set is fetched lazily and cached, so this costs nothing when Google sign-in
 * is not configured or not used.
 */
@Configuration
public class GoogleJwtConfig {

    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    JwtDecoder googleJwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
    }
}
