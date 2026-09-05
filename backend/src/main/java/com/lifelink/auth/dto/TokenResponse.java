package com.lifelink.auth.dto;

public record TokenResponse(String accessToken, String refreshToken) {
}
