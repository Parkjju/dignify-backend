package com.rta.dignify.dto.admin;

/// 한글 보강 한 배치의 결과. remaining이 0이 되면 큐가 빈 것이다.
/// matched < checked는 정상 — KR 스토어프론트에 그 곡이 없으면 매칭이 안 되고, 재조회를
/// 막으려고 ko_checked만 true로 찍는다.
public record KoBatch(int checked, int matched, long remaining) {
}
