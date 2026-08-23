package com.rta.dignify.repository;

import com.rta.dignify.domain.OnboardingCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OnboardingCandidateRepository extends JpaRepository<OnboardingCandidate, Long> {

    /// 비활성 곡은 제외한다 — 후보로 남아 있으면 프리뷰가 안 나오는 카드가 라운드에 뜬다.
    /// `Genre`가 LAZY라 별칭으로 같이 당긴다. 안 그러면 FeedItem을 만드는 자리에서 N+1이 난다.
    @Query("SELECT c FROM OnboardingCandidate c JOIN FETCH c.track t JOIN FETCH t.genre WHERE t.isActive = true")
    List<OnboardingCandidate> findAllActive();
}
