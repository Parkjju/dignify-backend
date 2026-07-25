package com.rta.dignify.repository;

import com.rta.dignify.domain.ListenedTrack;
import com.rta.dignify.dto.stats.ArtistCount;
import com.rta.dignify.dto.stats.GenreCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ListenedTrackRepository extends JpaRepository<ListenedTrack, Long> {

    @Query(value = """
    SELECT new com.rta.dignify.dto.stats.GenreCount(
        CASE WHEN :ko = true THEN g.genreNameKo ELSE g.genreNameEn END,
        COUNT(DISTINCT lt.track.id))
    FROM ListenedTrack lt
    JOIN lt.track t
    JOIN t.genre g
    WHERE lt.user.id = :userId AND lt.createdAt >= :since
    GROUP BY g.genreNameEn, g.genreNameKo
    ORDER BY COUNT(DISTINCT t.id) DESC, g.genreNameEn ASC
    """)
    List<GenreCount> countListenedTracksByGenre(@Param("userId") Long userId, @Param("since") Instant since, @Param("ko") boolean ko);

    @Query(value = """
    SELECT new com.rta.dignify.dto.stats.ArtistCount(
        CASE WHEN :ko = true THEN COALESCE(MAX(t.artistNameKo), t.artistName) ELSE t.artistName END, 
        COUNT(DISTINCT lt.track.id)
    )
    FROM ListenedTrack lt
    JOIN lt.track t
    WHERE lt.user.id = :userId AND lt.createdAt >= :since
    GROUP BY t.artistName
    ORDER BY COUNT(DISTINCT t.id) DESC, t.artistName ASC
    LIMIT 5
    """)
    List<ArtistCount> countListenedTracksByArtist(@Param("userId") Long userId, @Param("since") Instant since, @Param("ko") boolean ko);
}
