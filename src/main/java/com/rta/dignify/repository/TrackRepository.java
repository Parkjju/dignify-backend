package com.rta.dignify.repository;

import com.rta.dignify.domain.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findByGenreIdIn(List<Long> ids);

    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "WHERE t.genre_id IN :genreIds AND uht.user_hype_track_id IS NULL", nativeQuery = true)
    List<Track> findByGenreIdsExceptHypedTrack(@Param("userId") Long userId, @Param("genreIds") List<Long> genreIds);

    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "WHERE t.genre_id IN :genreIds AND uht.user_hype_track_id IS NULL " +
            "ORDER BY t.track_id " +
            "LIMIT :limit ", nativeQuery = true)
    List<Track> findByGenreIdsExceptHypedTrackWithLimit(@Param("userId") Long userId, @Param("genreIds") List<Long> genreIds, @Param("limit") Integer limit);

    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "WHERE t.genre_id IN :genreIds AND uht.user_hype_track_id IS NULL AND t.is_active IS TRUE " +
            "ORDER BY t.track_id " +
            "LIMIT :limit " +
            "OFFSET :offset", nativeQuery = true)
    List<Track> findByGenreIdsExceptHypedTrackWithLimitAndOffset(@Param("userId") Long userId, @Param("genreIds") List<Long> genreIds, @Param("limit") Integer limit, @Param("offset") Integer offset);
}
