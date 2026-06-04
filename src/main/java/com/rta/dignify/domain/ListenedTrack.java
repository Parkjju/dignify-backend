package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "listened_tracks")
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
public class ListenedTrack {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "listened_track_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false, updatable = false)
    private Track track;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ListenedTrack(User user, Track track) {
        this.user = user;
        this.track = track;
    }

    public static ListenedTrack create(User user, Track track) {
        return new ListenedTrack(user, track);
    }
}
