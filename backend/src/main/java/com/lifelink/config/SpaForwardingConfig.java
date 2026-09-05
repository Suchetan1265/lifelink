package com.lifelink.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The packaged build serves the React app from this application, and React
 * Router owns paths like {@code /donor/history}. Those have no controller, so
 * a reload or a pasted link would 404 without forwarding them to the bundle.
 *
 * <p>The patterns deliberately exclude anything containing a dot, so requests
 * for real files still reach the static handler rather than being handed the
 * HTML shell.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{path:[^.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{path:[^.]*}/{sub:[^.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{path:[^.]*}/{sub:[^.]*}/{leaf:[^.]*}")
                .setViewName("forward:/index.html");
    }
}
