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
            PushService.Alert alert = PushService.artistAddedAlert("Radiohead", note);

            assertThat(alert.title().key()).isEqualTo("push_artist_added_title");
            assertThat(alert.title().arg()).isEqualTo("Radiohead");
            assertThat(alert.body().key()).isEqualTo("push_artist_added_body");
            assertThat(alert.body().text()).isNull();
        }
    }

    @Test
    @DisplayName("아티스트 추가 푸시 — 문구를 주면 본문만 그 문구로 바뀐다")
    void artistAddedUsesNoteAsBody() {
        PushService.Alert alert = PushService.artistAddedAlert("Radiohead", "일부 앨범만 올라왔어요");

        assertThat(alert.body().text()).isEqualTo("일부 앨범만 올라왔어요");
        assertThat(alert.body().key()).isNull();
        assertThat(alert.title().key()).isEqualTo("push_artist_added_title");   // 제목은 그대로 기기 언어
    }

    @Test
    @DisplayName("반응 푸시 — 첫 반응은 닉네임을 본문에 담는다 (title은 한 줄이라 잘린다)")
    void pickReactionFirstNamesTheReactor() {
        PushService.Alert alert = PushService.pickReactionAlert("digger_kim", 1);

        assertThat(alert.title().key()).isEqualTo("push_pick_reaction_first_title");
        assertThat(alert.title().arg()).isNull();
        assertThat(alert.body().key()).isEqualTo("push_pick_reaction_first");
        assertThat(alert.body().arg()).isEqualTo("digger_kim");
    }

    @Test
    @DisplayName("반응 푸시 — 여럿이면 이름 대신 개수. 한 명만 대면 나머지가 지워진다")
    void pickReactionMilestoneCountsInstead() {
        PushService.Alert alert = PushService.pickReactionAlert("digger_kim", 5);

        assertThat(alert.title().key()).isEqualTo("push_pick_reaction_milestone_title");
        assertThat(alert.body().key()).isEqualTo("push_pick_reaction_milestone");
        assertThat(alert.body().arg()).isEqualTo("5");
    }

    /// 문구 분기는 위에서 다 봤으니, 여기선 그게 실제 APNs 페이로드까지 살아 나오는지만 본다.
    ///
    /// ponytail: FCM 쪽 대응 테스트는 없다 — firebase-admin의 `Message`/`AndroidNotification`은
    /// getter가 없어 조립 결과를 들여다볼 수가 없다. 두 렌더러가 같은 `Line`을 읽는 평평한
    /// 필드 매핑이라 분기 테스트가 양쪽을 같이 지킨다.
    @Test
    @DisplayName("APNs 페이로드 — loc-key와 인자가 그대로 실린다")
    void apnsPayloadCarriesLocKeys() {
        String payload = PushService.apnsPayload(PushService.pickReactionAlert("digger_kim", 1));

        assertThat(payload).contains("push_pick_reaction_first_title");
        assertThat(payload).contains("push_pick_reaction_first", "digger_kim");
        assertThat(payload).doesNotContain("push_pick_reaction_milestone");
    }

    @Test
    @DisplayName("APNs 페이로드 — 공지 푸시는 원문이 그대로 나간다")
    void apnsPayloadCarriesRawText() {
        String payload = PushService.apnsPayload(
                new PushService.Alert(PushService.Line.raw("제목"), PushService.Line.raw("본문")));

        assertThat(payload).contains("제목", "본문");
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
