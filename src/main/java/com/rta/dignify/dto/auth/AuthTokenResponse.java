package com.rta.dignify.dto.auth;

import java.time.Instant;

public record AuthTokenResponse(String refreshToken, String accessToken, Instant accessTokenExpiresAt) {
}
