package com.rta.dignify.dto.onboarding;

import com.rta.dignify.dto.feed.FeedItem;

import java.util.List;

/// 온보딩 2지선다 후보. 라운드 하나가 화면 하나(곡 두 개)다.
///
/// 곡은 피드의 `FeedItem`을 그대로 쓴다 — 앱이 같은 카드 구성을 쓰고 매퍼도 재사용된다.
/// 라운드 수는 서버가 정한다. 앱은 받은 만큼만 돌고, 후보가 아예 없으면 라운드를 건너뛴다.
public record OnboardingCandidatesResponse(List<Round> rounds) {
    /// `highTrackId`는 두 곡 중 어느 쪽이 축의 HIGH 극단인지 알려준다(items 순서는 셔플이라 위치로는 모른다).
    /// **앱은 유저가 고른 뒤에만 이 값을 쓴다** — 고르기 전에 어느 쪽이 어느 극단인지 보여주면
    /// 유저가 소리가 아니라 라벨을 고른다. 고른 뒤에는 반대로, 뭘 골랐는지 확인할 수 있어야
    /// "제대로 된 테스트"로 읽힌다.
    public record Round(String axis, Long highTrackId, List<FeedItem> items) {
    }
}
