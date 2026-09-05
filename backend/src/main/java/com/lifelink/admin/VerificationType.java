package com.lifelink.admin;

import com.lifelink.common.BadRequestException;

import java.util.Locale;

/** The {@code ?type=} filter on the admin verification queue (spec §5). */
public enum VerificationType {
    HOSPITAL,
    BLOODBANK;

    /** Case-insensitive so {@code ?type=hospital} works as the spec writes it. */
    public static VerificationType from(String raw) {
        if (raw != null) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to the shared error below
            }
        }
        throw new BadRequestException("type must be 'hospital' or 'bloodbank'");
    }
}
