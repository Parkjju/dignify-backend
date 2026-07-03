package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.dto.genre.GenreListResponse;
import com.rta.dignify.dto.genre.GenreResponse;
import com.rta.dignify.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class GenreService {
    private final GenreRepository genreRepository;

    @Transactional(readOnly = true)
    public GenreListResponse getGenreList() {
        List<Genre> genres = genreRepository.findGenresWithActiveTracks();
        List<GenreResponse> genreResponses = genres.stream()
                .map(GenreResponse::from)
                .toList();

        return new GenreListResponse(genreResponses);
    }
}
