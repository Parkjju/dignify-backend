package com.rta.dignify.repository;

import com.rta.dignify.domain.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findByGenreIdIn(List<Long> ids);

    @Query("SELECT t FROM Track t LEFT JOIN UserHypeTrack uht ON t = uht.track AND uht.user.id = :userId WHERE t.genre.id IN :genreIds AND uht IS NULL")
    List<Track> findByGenreIdsExceptHypedTrack(@Param("userId") Long userId, @Param("genreIds") List<Long> genreIds);

}
