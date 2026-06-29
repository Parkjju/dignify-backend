package com.rta.dignify.repository;

import com.rta.dignify.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByGenreNameEn(String genreNameEn);

    Optional<Genre> findByGenreNameKo(String genreNameKo);
}
