package com.rta.dignify.repository;

import com.rta.dignify.domain.UserHypeTrack;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserHypeTrackRepository extends JpaRepository<UserHypeTrack, Long> {
    boolean existsByUser_IdAndTrack_Id(Long userId, Long trackId);

    Optional<UserHypeTrack> findByUser_IdAndTrack_Id(Long userId, Long trackId);

    /**
     *
     * @param userId 유저 아이디
     * @param cursor 마지막에 fetch한 UserHypeTrack 리스트의 Last ID값
     * @param pageable 페이지네이션 객체
     * @return 유저 하입 트랙 리스트
     */
    @Query(value = "SELECT uht FROM UserHypeTrack uht " +
            "JOIN FETCH uht.track " +
            "WHERE uht.user.id = :userId " +
            "AND (:cursor IS NULL OR uht.id < :cursor) " +
            "ORDER BY uht.id DESC"

    )
    List<UserHypeTrack> findUserHypeTracksByUserId(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);
}
