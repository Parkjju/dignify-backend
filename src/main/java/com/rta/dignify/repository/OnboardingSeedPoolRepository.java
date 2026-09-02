package com.rta.dignify.repository;

import com.rta.dignify.domain.OnboardingSeedPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OnboardingSeedPoolRepository extends JpaRepository<OnboardingSeedPool, Long> {

    /// 비활성 곡은 뺀다 — 남아 있으면 프리뷰가 안 나오는 카드를 고르게 된다.
    /// `Genre`가 LAZY라 별칭으로 같이 당긴다(FeedItem이 장르명을 읽는다).
    @Query("SELECT s FROM OnboardingSeedPool s JOIN FETCH s.track t JOIN FETCH t.genre "
            + "WHERE t.isActive = true ORDER BY s.position")
    List<OnboardingSeedPool> findAllActiveOrdered();
}
