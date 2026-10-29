package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

/// 주간 추천 한 건 = 한 유저의 한 주. 기준 곡 재사용 방지·같은 주 중복 방지·효과 측정을 이 테이블 하나로 한다.
///
/// **같은 주 중복은 유니크 제약이 막는다.** 배치를 다시 돌려도 이미 있는 유저는 insert가 실패해 건너뛴다.
/// 발송 직전에 넣으므로 넣은 뒤 발송이 실패하면 그 주는 누락이다 — 중복보다 누락을 택했다.
///
/// 효과 측정: `track_ids`를 `listened_tracks`·`users_hype_tracks`와 조인하면 5곡 중 몇 곡을 듣고 좋아요했는지 나온다.
@Table(name = "weekly_picks",
        uniqueConstraints = @UniqueConstraint(name = "uq_weekly_pick_user_week", columnNames = {"user_id", "week_start"}))
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyPick extends BaseTimeEntity {

    @Id
    @Column(name = "weekly_pick_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seed_track_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track seedTrack;

    /// 추천 곡, 재생 순서대로. 발송 때 고정한다 — 나중에 탭해도 같은 곡이 나와야 한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "track_ids", nullable = false)
    private List<Long> trackIds;

    /// 발송 주의 월요일(한국 날짜).
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    /// `push_logs`·열람 이벤트를 잇는 키. 실행(시각)마다 달라 A/B 그룹도 이 값으로 갈린다.
    @Column(name = "push_id", length = 36, nullable = false)
    private String pushId;

    private WeeklyPick(User user, Track seedTrack, List<Long> trackIds, LocalDate weekStart, String pushId) {
        this.user = user;
        this.seedTrack = seedTrack;
        this.trackIds = trackIds;
        this.weekStart = weekStart;
        this.pushId = pushId;
    }

    public static WeeklyPick create(User user, Track seedTrack, List<Long> trackIds, LocalDate weekStart, String pushId) {
        return new WeeklyPick(user, seedTrack, trackIds, weekStart, pushId);
    }
}
