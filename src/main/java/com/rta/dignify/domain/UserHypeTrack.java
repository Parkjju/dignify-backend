package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users_hype_tracks", uniqueConstraints = @UniqueConstraint(name = "uq_user_track_ids", columnNames = {"user_id", "track_id"}))
@Entity
@Getter
public class UserHypeTrack extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_hype_track_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", updatable = false, nullable = false)
    private Track track;

    private UserHypeTrack(User user, Track track) {
        this.user = user;
        this.track = track;
    }

    public static UserHypeTrack create(User user, Track track) {
        return new UserHypeTrack(user, track);
    }
}
