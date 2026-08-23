package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/// 온보딩 소리 2지선다의 후보 곡. 축 하나의 양극단(HIGH/LOW)마다 몇 곡씩 넣어 두고
/// 라운드마다 거기서 랜덤으로 한 곡씩 뽑는다.
///
/// **곡 id를 앱에 박지 않는 이유가 이 테이블이다** — 후보 곡이 비활성화되면 온보딩이 통째로
/// 깨지는데, 여기 있으면 서버가 요청 시점에 걸러내고 같은 극단의 다른 곡을 내보낸다.
///
/// 채우는 건 `mood-pilot/pick-onboarding-candidates.py`가 만든 SQL이다(귀 검증 후 수동 실행).
/// 축 좌표는 DB에 없다 — `track_vectors`에는 PCA 32차원만 있고 "이 곡이 보컬 축의 극단인가"는
/// CLAP 텍스트 임베딩과 비교해야 나오는 값이라 오프라인에서 뽑는다.
@Table(name = "onboarding_candidates")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/// 생성 시각을 안 든다(`BaseTimeEntity` 미상속). 이 테이블은 손으로 시딩하는 고정 데이터라
/// 감사 컬럼이 NOT NULL로 붙으면 시드 SQL이 `(track_id, axis, pole)`만으로는 안 들어간다.
public class OnboardingCandidate {
    @Id
    @Column(name = "onboarding_candidate_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track track;

    /// 어느 축의 극단인지. vocal(보컬) · arousal(에너지) · acoustic(질감).
    /// 축 이름을 enum으로 굳히지 않는다 — 축을 갈아 끼우는 건 SQL 한 줄이어야 하고,
    /// 서버는 이 값으로 묶기만 할 뿐 의미를 해석하지 않는다.
    @Column(name = "axis", nullable = false, length = 16)
    private String axis;

    /// HIGH 또는 LOW. 한 라운드는 같은 축의 HIGH 한 곡 + LOW 한 곡으로 만들어진다.
    @Column(name = "pole", nullable = false, length = 4)
    private String pole;

    private OnboardingCandidate(Track track, String axis, String pole) {
        this.track = track;
        this.axis = axis;
        this.pole = pole;
    }

    public static OnboardingCandidate create(Track track, String axis, String pole) {
        return new OnboardingCandidate(track, axis, pole);
    }
}
