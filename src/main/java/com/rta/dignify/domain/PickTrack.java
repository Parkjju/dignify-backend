package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "pick_tracks", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pick_track", columnNames = {"pick_id", "track_id"}),
        @UniqueConstraint(name = "uq_pick_pos",   columnNames = {"pick_id", "position"})
})
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickTrack extends BaseTimeEntity {

    @Id
    @Column(name = "pick_track_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track track;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pick_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pick pick;

    @Column(name = "position", nullable = false)
    private Integer position;

    private PickTrack(Pick pick, Track track, Integer position) {
        this.pick = pick;
        this.track = track;
        this.position = position;
    }

    public static PickTrack create(Pick pick, Track track, Integer position) {
        return new PickTrack(pick, track, position);
    }
}
