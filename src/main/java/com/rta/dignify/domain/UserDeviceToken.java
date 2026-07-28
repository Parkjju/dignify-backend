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

    private UserDeviceToken(User user, String token, String environment, String timeZone) {
        this.user = user;
        this.token = token;
        this.environment = environment;
        this.timeZone = timeZone;
    }

    public static UserDeviceToken create(User user, String token, String environment, String timeZone) {
        return new UserDeviceToken(user, token, environment, timeZone);
    }

    /// 재등록 시 타임존이 비어 오면(구버전 앱) 기존 값을 지우지 않는다.
    public void reassign(User user, String environment, String timeZone) {
        this.user = user;
        this.environment = environment;
        if (timeZone != null && !timeZone.isBlank()) {
            this.timeZone = timeZone;
        }
    }
}