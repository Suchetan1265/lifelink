package com.lifelink.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Seeds the first ADMIN account; see {@link AdminBootstrap}. */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String email, String password) {
}
