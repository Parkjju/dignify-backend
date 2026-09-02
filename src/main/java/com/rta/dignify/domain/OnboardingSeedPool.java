package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/// 온보딩 첫 화면이 띄우는 곡 목록. 유저가 여기서 좋아하는 곡을 직접 고른다.
///
/// **정적이다.** 인기순 랭킹을 요청 시점에 계산하지 않는다 — 곡은 `listened_tracks` ·
/// `user_hype_tracks` 상위를 1회성 SQL로 뽑아 넣고, 갈아 끼우는 것도 손으로 한다.
/// 큐레이션 세트와 같은 운영 방식이고, 집계 쿼리는 요청 경로에 두지 않는다.
///
/// 후보 테이블 둘을 재사용하지 않은 이유가 있다(`TODO.md` P0). `curation_tracks`에 담으면
/// 그 곡들이 일반 피드에서 통째로 빠지고(`TrackRepository:43`), `onboarding_candidates`는
/// `axis`·`pole`이 NOT NULL이라 인기로 뽑은 곡에 축 값을 지어 넣어야 한다.
@Table(name = "onboarding_seed_pool")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/// `OnboardingCandidate`와 같이 생성 시각을 안 든다 — 손으로 시딩하는 고정 데이터라
/// 감사 컬럼이 NOT NULL로 붙으면 시드 SQL이 `(track_id, position)`만으로는 안 들어간다.
public class OnboardingSeedPool {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track track;

    /// 화면에 뿌릴 순서. 유니크를 안 건다 — 같은 값이 겹쳐도 목록이 한 번 흔들릴 뿐이고,
    /// 시드 SQL이 번호를 다시 매길 때 제약에 걸려 넘어지는 쪽이 더 아프다.
    @Column(name = "position", nullable = false)
    private Integer position;

    private OnboardingSeedPool(Track track, Integer position) {
        this.track = track;
        this.position = position;
    }

    public static OnboardingSeedPool create(Track track, Integer position) {
        return new OnboardingSeedPool(track, position);
    }
}
