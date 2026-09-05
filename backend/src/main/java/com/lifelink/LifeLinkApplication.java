package com.lifelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Authentication is JWT-only; without this Boot would also create an unused
// in-memory user and log a generated password on every start.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LifeLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeLinkApplication.class, args);
    }
}
