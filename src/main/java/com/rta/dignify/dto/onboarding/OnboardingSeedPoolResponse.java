package com.rta.dignify.dto.onboarding;

import com.rta.dignify.dto.feed.FeedItem;

import java.util.List;

/// 온보딩 곡 고르기 화면의 후보 목록. 피드의 `FeedItem`을 그대로 쓴다 — 앱이 같은 카드를 그린다.
///
/// 페이지네이션이 없다. 풀이 수십 곡이라 한 응답에 다 담긴다.
public record OnboardingSeedPoolResponse(List<FeedItem> items) {
}
