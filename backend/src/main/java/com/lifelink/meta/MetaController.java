package com.lifelink.meta;

import com.lifelink.common.BloodGroup;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Enum values + compatibility matrix for frontend dropdowns (spec §5, shared). */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    @org.springframework.beans.factory.annotation.Value("${app.google.client-id:}")
    private String googleClientId;

    /** Null when Google sign-in is not configured, so the client can hide the button. */
    public record AuthConfig(String googleClientId) {
    }

    @GetMapping("/auth-config")
    public AuthConfig authConfig() {
        return new AuthConfig(
                googleClientId == null || googleClientId.isBlank() ? null : googleClientId);
    }

    public record BloodGroupInfo(String group, List<String> canReceiveFrom) {
    }

    @GetMapping("/blood-groups")
    public List<BloodGroupInfo> bloodGroups() {
        return Arrays.stream(BloodGroup.values())
                .map(group -> new BloodGroupInfo(
                        group.label(),
                        group.compatibleDonors().stream().map(BloodGroup::label).toList()))
                .toList();
    }
}
