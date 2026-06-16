package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.dto.genre.GenreListResponse;
import com.rta.dignify.repository.GenreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class GenreServiceTest {
    @InjectMocks
    private GenreService genreService;

    @Mock
    private GenreRepository genreRepository;

    @Test
    @DisplayName("장르명 조회 API 테스트")
    void getGenreList() {
        Genre genre1 = Genre.create("Rock", "락");
        Genre genre2 = Genre.create("Ballad", "발라드");
        given(genreRepository.findAll()).willReturn(List.of(genre1, genre2));

        GenreListResponse response = genreService.getGenreList();
        assertThat(response.genres()).hasSize(2);
    }
}
