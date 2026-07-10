package com.rta.dignify.repository;

import com.rta.dignify.domain.CurationTrack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurationTrackRepository extends JpaRepository<CurationTrack, Long> {
    boolean existsByTrack_Id(Long trackId);
}
