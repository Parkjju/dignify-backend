package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "user_device_tokens", uniqueConstraints = @UniqueConstraint(name = "uq_device_token", columnNames = "token"), indexes = @Index(name = "idx_device_token_user_id", columnList = "user_id"))
@Entity @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeviceToken extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_device_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false, length = 20)
    private String environment;

    /// IANA 타임존(예: Asia/Seoul). 기기 로컬 시각으로 발송하기 위해 받는다 —
    /// 유저가 한국과 미국으로 갈려 있어 UTC 고정 발송은 한쪽이 반드시 새벽이 된다.
    /// 구버전 앱은 안 보내므로 nullable. null이면 발송 측에서 기본 타임존을 쓴다.
    @Column(name = "time_zone", length = 64)
    private String timeZone;

    /// 앱 빌드 번호(CFBundleVersion). 앱이 보내주는 게 아니라 URLSession 기본 User-Agent
    /// ("dignify/12 CFNetwork/... Darwin/...")에서 서버가 주워 담는다 — 그래야 이미 심사 통과해
    /// 배포된 버전까지 소급해서 갈라 쏠 수 있다. 못 읽으면 null이고, 발송 측은 null을 구버전으로 친다.
    @Column(name = "app_build")
    private Integer appBuild;

    /// 발송 경로를 가른다 — "android"면 FCM, 그 외(null 포함)는 APNs.
    /// **nullable인 게 중요하다**: 이미 심사 통과해 배포된 iOS 앱은 이 필드를 안 보내고,
    /// 기존 row도 전부 값이 없다. null을 iOS로 치면 그 둘이 그대로 계속 동작한다.
    @Column(length = 10)
    private String platform;

    private UserDeviceToken(User user, String token, String environment, String platform, String timeZone, Integer appBuild) {
        this.user = user;
        this.token = token;
        this.environment = environment;
        this.platform = platform;
        this.timeZone = timeZone;
        this.appBuild = appBuild;
    }

    public static UserDeviceToken create(User user, String token, String environment, String platform, String timeZone, Integer appBuild) {
        return new UserDeviceToken(user, token, environment, platform, timeZone, appBuild);
    }

    /// 재등록 시 타임존·빌드·플랫폼이 비어 오면(구버전 앱, UA 파싱 실패) 기존 값을 지우지 않는다.
    public void reassign(User user, String environment, String platform, String timeZone, Integer appBuild) {
        this.user = user;
        this.environment = environment;
        if (platform != null && !platform.isBlank()) {
            this.platform = platform;
        }
        if (timeZone != null && !timeZone.isBlank()) {
            this.timeZone = timeZone;
        }
        if (appBuild != null) {
            this.appBuild = appBuild;
        }
    }

    /// 발송 경로 판정은 여기 한 곳에서만 한다 — null=iOS 규칙이 흩어지면 한쪽만 고치게 된다.
    public boolean isAndroid() {
        return "android".equalsIgnoreCase(platform);
    }
}