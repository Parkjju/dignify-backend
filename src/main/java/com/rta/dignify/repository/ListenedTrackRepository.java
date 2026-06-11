package com.rta.dignify.repository;

import com.rta.dignify.domain.ListenedTrack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListenedTrackRepository extends JpaRepository<ListenedTrack, Long> {
}
