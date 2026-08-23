package com.rta.dignify.dto.user;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/// 상한이 없다. 예전엔 3개였는데(앱 피커도 3개로 맞춰져 있었다), 고른 장르가 곧 피드 풀이라
/// 상한이 곧 풀을 3/13으로 가두는 장치였다. 빈 리스트도 유효하다 — 그러면 전 카탈로그가 풀이 된다.
public record PreferGenreUpdateRequest(@NotNull List<Long> genreIds) {
}
