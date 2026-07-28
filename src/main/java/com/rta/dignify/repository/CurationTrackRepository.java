package com.rta.dignify.repository;

import com.rta.dignify.domain.CurationTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CurationTrackRepository extends JpaRepository<CurationTrack, Long> {

    /// 이번 주 세트. 전 유저 동일 내용이라 userId를 받지 않는다.
    /// 장르를 태우면 안 된다 — 유저 선호 장르와 안 맞아도 보여주는 게 큐레이션의 목적이다.
    @Query("SELECT ct FROM CurationTrack ct JOIN FETCH ct.track t JOIN FETCH t.genre " +
            "WHERE ct.isActive = TRUE AND t.isActive = TRUE ORDER BY ct.priority DESC, ct.id")
    List<CurationTrack> findActiveOrdered();
}
