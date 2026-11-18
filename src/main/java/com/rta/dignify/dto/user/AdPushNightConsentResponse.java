package com.rta.dignify.dto.user;

import java.time.Instant;

/// processedAt은 앱의 결과 토스트 「Dignify의 야간 광고성 정보를 YYYY-MM-DD에 동의했어요」에 쓴다.
public record AdPushNightConsentResponse(boolean nightPush, Instant processedAt) {
}
