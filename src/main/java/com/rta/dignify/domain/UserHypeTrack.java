package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "users_hype_tracks", uniqueConstraints = @UniqueConstraint(name = "uq_user_track_ids", columnNames = {"user_id", "track_id"}))
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserHypeTrack extends BaseTimeEntity {

    @Id
    @Column(name = "user_hype_track_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track track;

    /// 유저가 추천 기준으로 직접 고정한 곡인지. 하나라도 있으면 `MoodRecommender`가
    /// 최근 하입 대신 이것만 시드로 쓴다. User.diggingMode와 같은 이유로 DEFAULT가 필요하다.
    @ColumnDefault("false")
    @Column(name = "is_seed", nullable = false)
    private Boolean isSeed = false;

    private UserHypeTrack(User user, Track track) {
        this.user = user;
        this.track = track;
    }

    public static UserHypeTrack create(User user, Track track) {
        return new UserHypeTrack(user, track);
    }
}
