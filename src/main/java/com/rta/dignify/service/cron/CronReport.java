package com.rta.dignify.service.cron;

import com.rta.dignify.client.discord.DiscordNotifier.Embed;
import com.rta.dignify.client.discord.DiscordNotifier.Field;
import com.rta.dignify.client.discord.DiscordNotifier.Footer;
import com.rta.dignify.domain.PushLog;
import com.rta.dignify.domain.PushVariant;
import com.rta.dignify.service.NewReleaseService;
import com.rta.dignify.service.WeeklyPickService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/// 신곡 알림·주간 추천 크론 결과를 팀 디스코드 채널용 임베드로 만든다.
/// 기기 칸은 어드민 푸시 탭과 같은 축이다(전체 = 버전 제외 + 밤 제외 + 시도).
/// "발송 성공"은 Apple·Google 서버가 받아 갔다는 뜻이고 기기에 떴다는 뜻은 아니다.
/// 열람·수신거부는 발송 뒤에 쌓이므로 여기엔 없다 — push_id로 어드민 푸시 탭에서 본다.
public final class CronReport {
    static final int GREEN = 0x2ECC71;
    static final int GRAY = 0x95A5A6;
    static final int RED = 0xE74C3C;
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"));

    private CronReport() {
    }

    public static Embed newRelease(PushVariant.Slot slot, NewReleaseService.RunResult r, Instant startedAt, Duration took) {
        PushVariant group = PushVariant.newReleaseGroup(slot);
        List<Field> fields = new ArrayList<>();
        if (slot == PushVariant.Slot.AM) {
            fields.add(new Field("📥 신곡 수집", "좋아요한 아티스트 " + r.artists() + "명 확인\n새로 저장한 곡 " + r.saved() + "곡", true));
        }
        fields.add(new Field("🎵 알릴 신곡", r.newReleases() + "곡 (아티스트 " + r.releaseArtists() + "명)"
                + (slot == PushVariant.Slot.PM ? "\n오늘 오전에 저장한 곡" : ""), true));
        fields.add(new Field("👥 받을 사람", r.users() + "명\n" + group + "그룹 · 광고 수신 동의 · 그 아티스트 좋아요", true));

        String summary = r.log() == null ? null
                : r.users() + "명에게 신곡 " + r.newReleases() + "곡을 알렸습니다.";
        return build("🆕 신곡 알림", slot, group, startedAt, took, summary, fields, r.skipped(), r.log());
    }

    public static Embed weeklyPick(PushVariant.Slot slot, WeeklyPickService.RunResult r, Instant startedAt, Duration took) {
        PushVariant group = PushVariant.weeklyPickGroup(slot);
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("📆 추천 주간", r.weekStart() + " (월) 시작", true));
        fields.add(new Field("👥 받을 사람", r.targets() + "명\n" + group + "그룹 · 광고 수신 동의 · 좋아요 4곡 이상", true));
        if (r.log() != null) {
            fields.add(new Field("🎧 추천 생성", r.picked() + "명\n건너뜀 " + (r.targets() - r.picked())
                    + "명 (이번 주 추천이 이미 있거나 기준 곡이 없음)", true));
        }

        String summary = r.log() == null ? null : r.picked() + "명에게 이번 주 추천 5곡을 알렸습니다.";
        return build("📅 주간 추천", slot, group, startedAt, took, summary, fields, r.skipped(), r.log());
    }

    public static Embed failed(String job, String slot, Exception e) {
        return new Embed("❌ " + job + " · " + slotName(slot) + " 실패",
                "실행 도중 오류가 나서 끝까지 돌지 못했습니다.\n```" + e.getClass().getSimpleName() + ": " + e.getMessage() + "```",
                RED, List.of(), new Footer("Cloud Run 로그에서 같은 시각을 찾아볼 것"));
    }

    private static Embed build(String job, PushVariant.Slot slot, PushVariant group, Instant startedAt, Duration took,
                               String summary, List<Field> fields, String skipped, PushLog log) {
        String title = job + " · " + slotName(slot.name()) + " 회차";
        String footer = WHEN.format(startedAt) + " KST · " + group + "그룹 · " + String.format("%.1f초", took.toMillis() / 1000.0);
        if (log == null) {
            fields.add(new Field("📭 발송", "보내지 않음\n이유: " + skipped, false));
            return new Embed(title, "이번 회차는 알림을 보내지 않았습니다.", GRAY, fields, new Footer(footer));
        }

        int sent = log.getAcceptedIos() + log.getAcceptedAndroid();
        fields.add(new Field("📱 대상 기기", "전체 " + log.getCandidates() + "대"
                + "\n· 앱 버전이 낮아 제외 " + log.getSkippedBuild() + "대"
                + "\n· 밤 시간이라 제외 " + log.getSkippedQuiet() + "대"
                + "\n· 발송 시도 " + log.getAttempted() + "대", true));
        String result = "성공 " + sent + "대 (iPhone " + log.getAcceptedIos() + " · Android " + log.getAcceptedAndroid() + ")"
                + "\n실패 " + log.getFailed() + "대";
        if (log.pending() > 0) result += "\n응답 없음 " + log.pending() + "대";
        fields.add(new Field("✅ 발송 결과", result, true));
        fields.add(new Field("📊 발송률", "**" + percent(sent, log.getAttempted()) + "**\n성공 " + sent + " ÷ 시도 " + log.getAttempted(), true));
        if (!log.getFailureReasons().isEmpty()) {
            String reasons = log.getFailureReasons().entrySet().stream()
                    .map(e -> "· " + e.getKey().replace("ios:", "iPhone ").replace("android:", "Android ") + " " + e.getValue() + "대")
                    .collect(Collectors.joining("\n"));
            if (log.getInvalidated() > 0) reasons += "\n만료된 기기 토큰 " + log.getInvalidated() + "대는 자동으로 지웠습니다.";
            fields.add(new Field("⚠️ 실패 이유", reasons, false));
        }
        return new Embed(title, summary, sent == log.getAttempted() ? GREEN : 0xF1C40F, fields,
                new Footer(footer + " · push_id " + log.getPushId() + "\n열람 수·수신거부 수는 어드민 푸시 탭에서 확인"));
    }

    private static String slotName(String slot) {
        return "am".equalsIgnoreCase(slot) ? "오전 09:05" : "오후 18:05";
    }

    static String percent(int part, int whole) {
        return whole == 0 ? "-" : String.format("%.1f%%", part * 100.0 / whole);
    }
}
