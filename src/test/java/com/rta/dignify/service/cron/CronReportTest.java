package com.rta.dignify.service.cron;

import com.rta.dignify.client.discord.DiscordNotifier;
import com.rta.dignify.client.discord.DiscordNotifier.Embed;
import com.rta.dignify.domain.PushLog;
import com.rta.dignify.domain.PushVariant;
import com.rta.dignify.service.NewReleaseService;
import com.rta.dignify.service.WeeklyPickService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/// 샘플 숫자로 리포트 문구를 만든다. `DISCORD_WEBHOOK_URL`을 주고 돌리면 팀 채널에 실제로 올려서 모양을 본다.
class CronReportTest {
    private static final Instant AT = Instant.parse("2026-10-31T00:05:00Z");   // 한국 09:05

    @Test
    void newReleaseSent() {
        Embed e = CronReport.newRelease(PushVariant.Slot.AM,
                new NewReleaseService.RunResult(42, 12, 5, 3, 8, 9, null, sampleLog()), AT, Duration.ofMillis(38_200));

        assertThat(e.title()).isEqualTo("🆕 신곡 알림 · 오전 09:05 회차");
        assertThat(e.footer().text()).startsWith("10/31(토) 09:05 KST · A그룹 · 38.2초");
        assertThat(text(e)).contains("새로 저장한 곡 12곡")
                .contains("전체 11대", "앱 버전이 낮아 제외 1대", "밤 시간이라 제외 1대", "발송 시도 9대")
                .contains("성공 8대 (iPhone 6 · Android 2)", "실패 1대")
                .contains("**88.9%**", "성공 8 ÷ 시도 9")
                .contains("iPhone BadDeviceToken 1대", "만료된 기기 토큰 1대");
        send(e);
    }

    @Test
    void weeklyPickSent() {
        Embed e = CronReport.weeklyPick(PushVariant.Slot.AM,
                new WeeklyPickService.RunResult(LocalDate.of(2026, 11, 2), 8, 6, 9, null, sampleLog()), AT, Duration.ofMillis(1_200));

        assertThat(e.title()).isEqualTo("📅 주간 추천 · 오전 09:05 회차");
        assertThat(e.footer().text()).contains("B그룹");
        assertThat(text(e)).contains("6명\n건너뜀 2명", "**88.9%**");
        send(e);
    }

    @Test
    void skippedHasReasonAndNoDeviceFields() {
        Embed e = CronReport.newRelease(PushVariant.Slot.PM,
                new NewReleaseService.RunResult(0, 0, 0, 0, 0, 0, "광고성 발송 꺼짐(PUSH_AD_ENABLED=false)", null),
                AT, Duration.ofMillis(300));

        assertThat(e.color()).isEqualTo(CronReport.GRAY);
        assertThat(text(e)).contains("이유: 광고성 발송 꺼짐").doesNotContain("신곡 수집").doesNotContain("대상 기기");
        assertThat(CronReport.percent(0, 0)).isEqualTo("-");
        send(e);
    }

    private static String text(Embed e) {
        return e.description() + e.fields().stream().map(f -> f.name() + "\n" + f.value()).reduce("", String::concat);
    }

    /// 후보 11 = 구버전 1 + 야간 1 + 시도 9, 시도 9 = 수락 8 + 실패 1.
    private static PushLog sampleLog() {
        PushLog log = PushLog.start("new_release_am", "new_release", "신곡", "(광고) 샘플", null, null, false);
        log.candidates(11);
        log.skippedBuild();
        log.skippedQuiet();
        for (int i = 0; i < 9; i++) log.attempted();
        for (int i = 0; i < 6; i++) log.accepted(false);
        for (int i = 0; i < 2; i++) log.accepted(true);
        log.failed(1L, 1L, false, "BadDeviceToken", true);
        return log;
    }

    private static void send(Embed e) {
        String url = System.getenv("DISCORD_WEBHOOK_URL");
        if (url != null && !url.isBlank()) {
            new DiscordNotifier(url).notify(new Embed("🧪 [테스트·샘플 숫자] " + e.title(), e.description(), e.color(), e.fields(), e.footer()));
        }
    }
}
