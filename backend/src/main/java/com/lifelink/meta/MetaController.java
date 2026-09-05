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
