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

    private CurationTrack(Track track, int priority) {
        this.track = track;
        this.priority = priority;
    }

    /// 세트는 priority DESC로 나가므로, 앞자리에 둘 곡일수록 큰 값을 받는다.
    public static CurationTrack create(Track track, int priority) {
        return new CurationTrack(track, priority);
    }

    /// 세트에서 뺀 곡은 지우지 않고 끈다. 다시 넣을 때 같은 행을 되살리면 되고(track_id가 unique),
    /// 언제 어떤 곡을 걸었는지 기록도 남는다.
    public void deactivate() {
        this.isActive = false;
    }

    public void activate(int priority) {
        this.priority = priority;
        this.isActive = true;
    }
}
