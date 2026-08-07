package com.rta.dignify.repository;

import com.rta.dignify.domain.ArtistRequest;
import com.rta.dignify.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArtistRequestRepository extends JpaRepository<ArtistRequest, Long> {
    List<ArtistRequest> findByUserIdOrderByIdDesc(Long userId);

    /// 어드민 대기 목록. 요청자 닉네임을 같이 보여주므로 유저를 함께 가져온다.
    @Query("SELECT ar FROM ArtistRequest ar JOIN FETCH ar.user WHERE ar.status = :status ORDER BY ar.id")
    List<ArtistRequest> findByStatusWithUser(@Param("status") RequestStatus status);
}
