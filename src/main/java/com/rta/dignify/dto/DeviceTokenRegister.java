package com.rta.dignify.dto;

import jakarta.validation.constraints.NotBlank;

/// timeZone은 구버전 앱이 안 보내므로 필수가 아니다. 없으면 서버 기본값으로 발송한다.
public record DeviceTokenRegister(@NotBlank String token, @NotBlank String environment, String timeZone) {
}
