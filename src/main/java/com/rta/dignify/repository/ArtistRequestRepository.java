package com.rta.dignify.repository;

import com.rta.dignify.domain.ArtistRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistRequestRepository extends JpaRepository<ArtistRequest, Long> {
    List<ArtistRequest> findByUserIdOrderByIdDesc(Long userId);
}
