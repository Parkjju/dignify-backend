package com.rta.dignify.dto.onboarding;

import com.rta.dignify.dto.feed.FeedItem;

import java.util.List;

/// 온보딩 2지선다 후보. 라운드 하나가 화면 하나(곡 두 개)다.
///
/// 곡은 피드의 `FeedItem`을 그대로 쓴다 — 앱이 같은 카드 구성을 쓰고 매퍼도 재사용된다.
/// 라운드 수는 서버가 정한다. 앱은 받은 만큼만 돌고, 후보가 아예 없으면 라운드를 건너뛴다.
public record OnboardingCandidatesResponse(List<Round> rounds) {
    public record Round(String axis, List<FeedItem> items) {
    }
}
