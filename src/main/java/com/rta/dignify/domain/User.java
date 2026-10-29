package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Table(name = "users")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false, length = 20)
    private String nickname;

    @Column(name = "is_onboarding_complete", nullable = false)
    private Boolean isOnboardingComplete = false;

    /// 디깅 성향. 켜져 있으면 피드가 하입한 곡과 무드가 가까운 순으로 오고, 꺼져 있으면
    /// 제약 없는 무작위로 온다. **기본값은 켜짐이라 지금 동작이 그대로 유지된다.**
    ///
    /// `@ColumnDefault`가 필요한 이유는 이미 행이 있는 테이블에 NOT NULL 컬럼을 붙이기
    /// 때문이다. DEFAULT 없이는 ddl-auto=update의 ALTER TABLE이 실패해 앱이 안 뜬다.
    @ColumnDefault("true")
    @Column(name = "digging_mode", nullable = false)
    private Boolean diggingMode = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /// 광고성 알림(큐레이션·신곡·주간 추천) 수신 동의. **OS 알림 권한과 별개다** — 권한은 광고 수신
    /// 동의가 아니다(정보통신망법 제50조, KISA 안내서 Q&A 02-03). 기본 false라 동의 팝업을 거친 유저만 true다.
    @ColumnDefault("false")
    @Column(name = "ad_push_consent", nullable = false)
    private Boolean adPushConsent = false;

    /// 마지막 동의·거부 처리 시각. 앱의 결과 통지(제50조 7항)와 2년 재확인(8항)의 기준이다.
    @Column(name = "ad_push_consent_at")
    private Instant adPushConsentAt;

    /// 무드 피드 진행 위치. null이면 처음부터다(게스트·시드 없는 유저는 안 쓴다).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feed_state")
    private FeedState feedState;

    private User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }

    public static User create(String email, String nickname) {
        return new User(email, nickname);
    }

    public void deleteUser() {
        this.deletedAt = Instant.now();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void completeOnboarding() {
        this.isOnboardingComplete = true;
    }

    public void changeDiggingMode(boolean enabled) {
        this.diggingMode = enabled;
    }

    public void changeAdPushConsent(boolean consent, Instant now) {
        this.adPushConsent = consent;
        this.adPushConsentAt = now;
    }

    public void updateFeedState(FeedState feedState) {
        this.feedState = feedState;
    }
}
