package com.rta.dignify.dto;

import jakarta.validation.constraints.NotBlank;

/// timeZone은 구버전 앱이 안 보내므로 필수가 아니다. 없으면 서버 기본값으로 발송한다.
///
/// platform도 같은 이유로 필수가 아니다 — 이미 배포된 iOS 앱은 안 보낸다. 없으면 iOS로 친다.
/// 안드로이드 앱만 "android"를 보내고, 그 값이 APNs/FCM 발송 경로를 가른다.
///
/// environment는 APNs의 sandbox/production 분기용이라 FCM엔 대응 개념이 없다.
/// 안드로이드는 아무 값이나 보내도 발송에 영향이 없다(필수 필드라 비울 수는 없다).
public record DeviceTokenRegister(@NotBlank String token, @NotBlank String environment, String platform, String timeZone) {
}
