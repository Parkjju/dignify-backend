package com.rta.dignify.dto.user;

import com.rta.dignify.dto.genre.GenreResponse;

import java.util.List;

public record UserProfileResponse(String nickname, boolean isOnboardingComplete, List<GenreResponse> genres, boolean diggingMode,
                                  /// 광고성 알림 수신 동의. 앱 알림 설정 화면의 토글이 이 값으로 시작한다.
                                  boolean adPushConsent) {
}