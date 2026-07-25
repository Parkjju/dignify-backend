package com.rta.dignify.repository;

import com.rta.dignify.domain.UserHypeTrack;
import com.rta.dignify.dto.stats.ArtistCount;
import com.rta.dignify.dto.stats.GenreCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    @Query(value = "SELECT uht FROM UserHypeTrack uht " +
            "JOIN FETCH uht.user " +
            "WHERE uht.track.id = :trackId " +
            "ORDER BY uht.createdAt ASC " +
            "LIMIT 5"
    )
    List<UserHypeTrack> findFirstFiveHypeUsers(@Param("trackId") Long trackId);

    @Query(value = """
    SELECT new com.rta.dignify.dto.stats.GenreCount(
        CASE WHEN :ko = TRUE THEN g.genreNameKo ELSE g.genreNameEn END,
        COUNT(DISTINCT t.id)
    )
    FROM UserHypeTrack uht
    JOIN uht.track t
    JOIN t.genre g
    WHERE uht.user.id = :userId AND uht.createdAt >= :since
    GROUP BY g.genreNameEn, g.genreNameKo
    ORDER BY COUNT(DISTINCT t.id) DESC, g.genreNameEn ASC
    """)
    List<GenreCount> countUserHypeTracksByGenre(@Param("userId") Long userId, @Param("since") Instant since, @Param("ko") boolean ko);

    @Query(value = """
    SELECT new com.rta.dignify.dto.stats.ArtistCount(      
        CASE WHEN :ko = TRUE THEN COALESCE(MAX(t.artistNameKo), t.artistName) ELSE t.artistName END,
        COUNT(DISTINCT t.id)
    )
    FROM UserHypeTrack uht
    JOIN uht.track t
    WHERE uht.user.id = :userId AND uht.createdAt >= :since
    GROUP BY t.artistName
    ORDER BY COUNT(DISTINCT t.id) DESC, t.artistName ASC
    LIMIT 5
    """)
    List<ArtistCount> countUserHypeTracksByArtist(@Param("userId") Long userId, @Param("since") Instant since, @Param("ko") boolean ko);
}
