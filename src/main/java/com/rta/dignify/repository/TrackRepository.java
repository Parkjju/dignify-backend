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
            "WHERE uht.user_hype_track_id IS NULL AND t.is_active IS TRUE " +
            "ORDER BY t.track_id " +
            "LIMIT :limit " +
            "OFFSET :offset", nativeQuery = true)
    List<Track> findByGenreIdsExceptHypedTrackWithLimitAndOffset(@Param("userId") Long userId, @Param("limit") Integer limit, @Param("offset") Integer offset);

    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "LEFT JOIN user_genres ug ON ug.genre_id = t.genre_id AND ug.user_id = :userId " +
            "WHERE uht.user_hype_track_id IS NULL AND t.is_active IS TRUE AND ug.user_genre_id IS NULL " +
            "ORDER BY t.track_id " +
            "LIMIT :limit " +
            "OFFSET :offset", nativeQuery = true)
    List<Track> findGeneralTracksByGenreIdsExceptHypedTrackWithLimitAndOffset(@Param("userId") Long userId, @Param("limit") Integer limit, @Param("offset") Integer offset);
}
