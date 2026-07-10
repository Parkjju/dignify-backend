package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "curation_tracks")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurationTrack extends BaseTimeEntity {
    @Id
    @Column(name = "curation_track_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", unique = true, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track track;

    @Column(name = "priority", nullable = false, columnDefinition = "integer default 0")
    private Integer priority = 0;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
    private Boolean isActive = true;

    // priority 0 = 해당 장르 피드에 부스트 없이 노출 (curation-priority-zero-boost 결정). 필요 시 나중에 승급.
    public static CurationTrack create(Track track) {
        CurationTrack curationTrack = new CurationTrack();
        curationTrack.track = track;
        return curationTrack;
    }
}
