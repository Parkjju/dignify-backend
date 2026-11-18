package com.rta.dignify.dto.user;

import com.rta.dignify.dto.genre.GenreResponse;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(String nickname, boolean isOnboardingComplete, List<GenreResponse> genres, boolean diggingMode,
                                  /// 광고성 알림 수신 동의. 앱 알림 설정 화면의 토글이 이 값으로 시작한다.
                                  boolean adPushConsent,
                                  /// 마지막 동의·거부 처리 시각. null = 미응답 — 앱 첫 실행 동의 팝업 조건.
                                  Instant adPushConsentAt,
                                  /// 야간 광고성 동의. null = 미응답 — 앱 야간 동의 팝업 조건.
                                  Boolean adPushNightConsent,
                                  Instant adPushNightConsentAt) {
}