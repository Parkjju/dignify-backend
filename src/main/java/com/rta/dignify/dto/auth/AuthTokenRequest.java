package com.rta.dignify.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthTokenRequest(@NotBlank String refreshToken) {
}
