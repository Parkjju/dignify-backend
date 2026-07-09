package com.rta.dignify.repository;

import com.rta.dignify.domain.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {
    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "JOIN user_genres ug ON ug.genre_id = t.genre_id AND ug.user_id = :userId " +
            "LEFT JOIN curation_tracks c ON c.track_id = t.track_id AND c.is_active IS TRUE " +
            "WHERE uht.user_hype_track_id IS NULL AND t.is_active IS TRUE " +
            "ORDER BY COALESCE(c.priority, 0) DESC, md5(t.track_id::text || ':' || CAST(:seed AS text)) " +
            "LIMIT :limit " +
            "OFFSET :offset", nativeQuery = true)
    List<Track> findByGenreIdsExceptHypedTrackWithLimitAndOffset(@Param("userId") Long userId, @Param("limit") Integer limit, @Param("offset") Integer offset, @Param("seed") Integer seed);

    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "LEFT JOIN user_genres ug ON ug.genre_id = t.genre_id AND ug.user_id = :userId " +
            "LEFT JOIN curation_tracks c ON c.track_id = t.track_id AND c.is_active IS TRUE " +
            "WHERE uht.user_hype_track_id IS NULL AND t.is_active IS TRUE AND ug.user_genre_id IS NULL " +
            "ORDER BY COALESCE(c.priority, 0) DESC, md5(t.track_id::text || ':' || CAST(:seed AS text)) " +
            "LIMIT :limit " +
            "OFFSET :offset", nativeQuery = true)
    List<Track> findGeneralTracksByGenreIdsExceptHypedTrackWithLimitAndOffset(@Param("userId") Long userId, @Param("limit") Integer limit, @Param("offset") Integer offset, @Param("seed") Integer seed);

    @Query(value = "SELECT t FROM Track t " +
            "WHERE (LOWER(t.artistName) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) OR LOWER(t.trackName) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
            "OR LOWER(t.artistNameKo) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) OR LOWER(t.trackNameKo) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) ) AND t.isActive = TRUE " +
            "ORDER BY t.id " +
            "LIMIT :limit " +
            "OFFSET :offset"
    )
    List<Track> findTracksWithSearchKeyword(@Param("searchKeyword") String searchKeyword, @Param("limit") Integer limit, @Param("offset") Integer offset);

    boolean existsByExternalIdAndSource(String externalId, String source);

    @Query("SELECT t.externalId FROM Track t WHERE t.koChecked = FALSE ORDER BY t.id LIMIT :limit")
    List<String> findUncheckedExternalIds(@Param("limit") Integer limit);

    List<Track> findByExternalIdIn(List<String> externalIds);

    long countByKoCheckedFalse();
}
