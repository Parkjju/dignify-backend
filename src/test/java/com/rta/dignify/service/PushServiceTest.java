package com.rta.dignify.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/// 공지 푸시의 발송 시간대 판정. 같은 순간이라도 기기 타임존에 따라 갈린다.
class PushServiceTest {
    // 2026-07-28 04:00Z = 서울 13시, 피닉스(UTC-7) 21시, 뉴욕(UTC-4) 0시
    private static final Instant NOON_IN_SEOUL = Instant.parse("2026-07-28T04:00:00Z");

    @Test
    @DisplayName("기기 로컬 시각이 발송 시간대 안이면 보낸다")
    void awakeHourInWindow() {
        assertThat(PushService.isAwakeHour("Asia/Seoul", NOON_IN_SEOUL)).isTrue();
        assertThat(PushService.isAwakeHour("America/Phoenix", NOON_IN_SEOUL)).isTrue();
    }

    @Test
    @DisplayName("같은 순간이어도 로컬이 자정이면 건너뛴다")
    void skipsMidnightElsewhere() {
        assertThat(PushService.isAwakeHour("America/New_York", NOON_IN_SEOUL)).isFalse();
    }

    @Test
    @DisplayName("minBuild가 없으면 빌드를 안 봐도 전부 나간다")
    void noMinBuildSendsToAll() {
        assertThat(PushService.meetsMinBuild(12, null)).isTrue();
        assertThat(PushService.meetsMinBuild(null, null)).isTrue();
    }

    @Test
    @DisplayName("minBuild가 있으면 기준 미만과 빌드 미확인 기기는 빠진다")
    void minBuildExcludesOlderAndUnknown() {
        assertThat(PushService.meetsMinBuild(12, 12)).isTrue();
        assertThat(PushService.meetsMinBuild(13, 12)).isTrue();
        assertThat(PushService.meetsMinBuild(11, 12)).isFalse();
        assertThat(PushService.meetsMinBuild(null, 12)).isFalse();
    }

    @Test
    @DisplayName("아티스트 추가 푸시 — 문구를 안 주면 본문은 loc-key로 나간다")
    void artistAddedUsesLocKeyByDefault() {
        for (String note : new String[]{null, "", "  "}) {
            String payload = PushService.artistAddedPayload("Radiohead", note);
            assertThat(payload).contains("push_artist_added_body");
            assertThat(payload).contains("push_artist_added_title", "Radiohead");
        }
    }

    @Test
    @DisplayName("아티스트 추가 푸시 — 문구를 주면 본문만 그 문구로 바뀐다")
    void artistAddedUsesNoteAsBody() {
        String payload = PushService.artistAddedPayload("Radiohead", "일부 앨범만 올라왔어요");

        assertThat(payload).contains("일부 앨범만 올라왔어요");
        assertThat(payload).doesNotContain("push_artist_added_body");
        assertThat(payload).contains("push_artist_added_title");   // 제목은 그대로 기기 언어
    }

    @Test
    @DisplayName("반응 푸시 — 첫 반응은 닉네임을 본문에 담는다 (title은 한 줄이라 잘린다)")
    void pickReactionFirstNamesTheReactor() {
        String payload = PushService.pickReactionPayload("digger_kim", 1);

        assertThat(payload).contains("push_pick_reaction_first_title");
        assertThat(payload).contains("push_pick_reaction_first", "digger_kim");
        assertThat(payload).doesNotContain("push_pick_reaction_milestone");
    }

    @Test
    @DisplayName("반응 푸시 — 여럿이면 이름 대신 개수. 한 명만 대면 나머지가 지워진다")
    void pickReactionMilestoneCountsInstead() {
        String payload = PushService.pickReactionPayload("digger_kim", 5);

        assertThat(payload).contains("push_pick_reaction_milestone_title");
        assertThat(payload).contains("push_pick_reaction_milestone", "5");
        assertThat(payload).doesNotContain("digger_kim");
    }

    @Test
    @DisplayName("타임존이 없거나 이상하면 UTC로 친다 — 발송이 죽지 않는다")
    void fallsBackToUtc() {
        // 04:00Z는 UTC 기준 새벽이라 건너뛰는 쪽
        assertThat(PushService.isAwakeHour(null, NOON_IN_SEOUL)).isFalse();
        assertThat(PushService.isAwakeHour("Mars/Olympus", NOON_IN_SEOUL)).isFalse();
        // 12:00Z는 UTC 기준 낮이라 보내는 쪽
        assertThat(PushService.isAwakeHour("", Instant.parse("2026-07-28T12:00:00Z"))).isTrue();
    }
}
