package com.lifelink.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stored in the DB by enum name (e.g. A_POS); serialized in JSON by label
 * (e.g. "A+"), which is also accepted on input.
 */
public enum BloodGroup {
    O_NEG("O-"), O_POS("O+"), A_NEG("A-"), A_POS("A+"),
    B_NEG("B-"), B_POS("B+"), AB_NEG("AB-"), AB_POS("AB+");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static BloodGroup fromLabel(String value) {
        for (BloodGroup group : values()) {
            if (group.label.equalsIgnoreCase(value) || group.name().equalsIgnoreCase(value)) {
                return group;
            }
        }
        throw new IllegalArgumentException("Unknown blood group: " + value);
    }

    /** Donor groups whose blood a recipient of this group can receive. */
    public Set<BloodGroup> compatibleDonors() {
        return switch (this) {
            case O_NEG -> EnumSet.of(O_NEG);
            case O_POS -> EnumSet.of(O_NEG, O_POS);
            case A_NEG -> EnumSet.of(O_NEG, A_NEG);
            case A_POS -> EnumSet.of(O_NEG, O_POS, A_NEG, A_POS);
            case B_NEG -> EnumSet.of(O_NEG, B_NEG);
            case B_POS -> EnumSet.of(O_NEG, O_POS, B_NEG, B_POS);
            case AB_NEG -> EnumSet.of(O_NEG, A_NEG, B_NEG, AB_NEG);
            case AB_POS -> EnumSet.allOf(BloodGroup.class);
        };
    }
}
