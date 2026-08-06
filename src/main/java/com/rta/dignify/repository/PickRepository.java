package com.rta.dignify.repository;

import com.rta.dignify.domain.Pick;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PickRepository extends JpaRepository<Pick, Long> {
    // 1. 첫페이지 curOfficial NULL fetch
    // 2. official 현재 동일 단계에서 pickId 이어 읽기
    // 3. official FALSE인 케이스 다 읽으면 시딩 단계로 넘어감
    @Query(value = "SELECT p FROM Pick p " +
            "JOIN FETCH p.user " +
            "WHERE p.isDeleted = FALSE " +
            "AND (:curOfficial IS NULL OR (p.isOfficial = :curOfficial AND p.id < :curPickId) OR (:curOfficial = FALSE AND p.isOfficial = TRUE)) " +
            "ORDER BY p.isOfficial ASC, p.id DESC"
    )
    List<Pick> findPage(@Param("curOfficial") Boolean curOfficial, @Param("curPickId") Long curPickId, Pageable pageable);
}
