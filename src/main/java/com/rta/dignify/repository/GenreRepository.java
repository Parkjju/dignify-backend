package com.rta.dignify.repository;

import com.rta.dignify.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByGenreNameEn(String genreNameEn);

    Optional<Genre> findByGenreNameKo(String genreNameKo);

    @Query(value = "SELECT DISTINCT g.* FROM genres g " +
            "JOIN tracks t ON t.genre_id = g.genre_id AND t.is_active IS TRUE",
            nativeQuery = true)
    List<Genre> findGenresWithActiveTracks();
}
