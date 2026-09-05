package com.lifelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LifeLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeLinkApplication.class, args);
    }
}
