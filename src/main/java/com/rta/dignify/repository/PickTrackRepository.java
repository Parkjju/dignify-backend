package com.rta.dignify.repository;

import com.rta.dignify.domain.PickTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PickTrackRepository extends JpaRepository<PickTrack, Long> {
    @Query(value = "SELECT pt FROM PickTrack pt " +
            "JOIN FETCH pt.track " +
            "WHERE pt.pick.id IN :pickIds " +
            "ORDER BY pt.pick.id, pt.position"
    )
    List<PickTrack> findPickTracksByPickIds(@Param("pickIds") List<Long> pickIds);
}
