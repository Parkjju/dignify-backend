package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "listened_tracks",
        indexes = {
                @Index(name = "idx_listened_track_user_id", columnList = "user_id"),
                @Index(name = "idx_listened_track_track_id", columnList = "track_id")
        })
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListenedTrack extends BaseTimeEntity {

    @Id
    @Column(name = "listened_track_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track track;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    private ListenedTrack(User user, Track track) {
        this.user = user;
        this.track = track;
    }

    public static ListenedTrack create(User user, Track track) {
        return new ListenedTrack(user, track);
    }
}
