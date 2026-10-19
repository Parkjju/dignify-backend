package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 발송 한 번(공지 1회, 반응 알림 1회)의 결과. 어드민 푸시 탭이 읽는다.
///
/// **기기별 행을 따로 두지 않는다.** 발송마다 기기 수만큼 쌓이면 유저 수에 비례해 커진다.
/// 대신 한 행에 집계(수락/실패/무효 토큰 수, 실패 사유별 수)를 담고, 실패 기기만 앞의
/// FAILURE_SAMPLE개까지 남긴다. 행 수도 `PushLogRepository.trim`이 최근 N건으로 묶는다.
///
/// "수락"은 APNs/FCM이 받아갔다는 뜻이지 기기에 떴다는 뜻이 아니다 — 두 서비스 다 기기 도착을
/// 서버에 알려주지 않는다. 눌러서 열린 수는 PostHog `app_opened_push{type}`에서 본다.
///
/// 응답은 APNs/FCM 콜백 스레드에서 동시에 들어오므로 집계 메서드는 전부 synchronized다.
@Table(name = "push_logs")
@Entity @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushLog {
    static final int FAILURE_SAMPLE = 20;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_log_id")
    private Long id;

    /// 페이로드에 `push_id`로 실리는 값. 앱이 `app_opened_push`에 그대로 붙여 보내면
    /// PostHog에서 이 발송을 눌러 연 수를 정확히 센다(열람률 = 연 사람 ÷ 수락).
    /// DB id 대신 미리 만든 UUID인 이유: 행은 응답이 다 온 뒤에야 저장되는데 id는 저장해야 나온다.
    @Column(name = "push_id", length = 36, unique = true)
    private String pushId;

    /// broadcast(전체 공지) · test(userId 지정 공지) · pick_reaction(반응 알림)
    @Column(nullable = false, length = 20)
    private String kind;

    /// 페이로드 type(notice/curation/pick_reaction). 열람 계측 축과 같은 값이다.
    @Column(nullable = false, length = 30)
    private String type;

    /// 공지는 원문, 반응 알림은 loc-key(인자). 기기가 렌더한 실제 문구는 서버가 모른다.
    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String body;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "min_build")
    private Integer minBuild;

    private boolean force;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /// 마지막 응답이 온 시각. 기다리다 끊었으면(broadcast의 대기 한도) null이다.
    @Column(name = "completed_at")
    private Instant completedAt;

    /// 대상 후보 기기 수. = 빌드 제외 + 새벽 제외 + 시도.
    private int candidates;

    @Column(name = "skipped_build")
    private int skippedBuild;

    @Column(name = "skipped_quiet")
    private int skippedQuiet;

    private int attempted;

    @Column(name = "accepted_ios")
    private int acceptedIos;

    @Column(name = "accepted_android")
    private int acceptedAndroid;

    private int failed;

    /// 만료 응답을 받아 그 자리에서 지운 토큰 수. 실패에 포함된다.
    private int invalidated;

    /// "ios:BadDeviceToken" → 3 처럼 플랫폼:사유별 실패 수.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_reasons")
    private Map<String, Integer> failureReasons = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_sample")
    private List<Failure> failureSample = new ArrayList<>();

    public record Failure(Long tokenId, Long userId, String platform, String reason) { }

    private PushLog(String kind, String type, String title, String body, Long targetUserId, Integer minBuild, boolean force) {
        this.kind = kind;
        this.type = type;
        this.title = cut(title, 500);
        this.body = cut(body, 1000);
        this.targetUserId = targetUserId;
        this.minBuild = minBuild;
        this.force = force;
        this.sentAt = Instant.now();
        this.pushId = UUID.randomUUID().toString();
    }

    public static PushLog start(String kind, String type, String title, String body, Long targetUserId, Integer minBuild, boolean force) {
        return new PushLog(kind, type, title, body, targetUserId, minBuild, force);
    }

    public synchronized void candidates(int n) { candidates = n; }

    public synchronized void skippedBuild() { skippedBuild++; }

    public synchronized void skippedQuiet() { skippedQuiet++; }

    public synchronized void attempted() { attempted++; }

    public synchronized void accepted(boolean android) {
        if (android) acceptedAndroid++;
        else acceptedIos++;
    }

    public synchronized void failed(Long tokenId, Long userId, boolean android, String reason, boolean invalidated) {
        String platform = android ? "android" : "ios";
        failed++;
        if (invalidated) this.invalidated++;
        failureReasons.merge(platform + ":" + reason, 1, Integer::sum);
        if (failureSample.size() < FAILURE_SAMPLE) {
            failureSample.add(new Failure(tokenId, userId, platform, reason));
        }
    }

    public synchronized void completed() { completedAt = Instant.now(); }

    /// 시도했는데 아직 응답이 없는 수. 0이 아니면 대기 한도에서 끊긴 것이다.
    public synchronized int pending() {
        return attempted - acceptedIos - acceptedAndroid - failed;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
