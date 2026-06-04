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
@Table(name = "users_hype_tracks", uniqueConstraints = @UniqueConstraint(name = "uq_user_track_ids", columnNames = {"user_id", "track_id"}))
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
public class UserHypeTrack {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_hype_track_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", updatable = false, nullable = false)
    private Track track;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private UserHypeTrack(User user, Track track) {
        this.user = user;
        this.track = track;
    }

    public static UserHypeTrack create(User user, Track track) {
        return new UserHypeTrack(user, track);
    }
}
