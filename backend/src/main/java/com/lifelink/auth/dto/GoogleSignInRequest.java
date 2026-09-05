package com.lifelink.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** The ID token Google Identity Services hands the browser. */
public record GoogleSignInRequest(@NotBlank String credential) {
}
