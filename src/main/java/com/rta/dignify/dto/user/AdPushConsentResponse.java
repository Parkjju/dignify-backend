package com.rta.dignify.dto.user;

import java.time.Instant;

/// processedAt은 앱이 결과 통지 문구("…[수신동의/수신거부]가 YYYY-MM-DD에 처리되었습니다")에 그대로 쓴다(제50조 7항).
public record AdPushConsentResponse(boolean adPush, Instant processedAt) {
}
