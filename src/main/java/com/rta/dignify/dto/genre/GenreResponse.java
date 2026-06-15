package com.rta.dignify.dto.genre;

import com.rta.dignify.domain.Genre;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

public record GenreResponse(Long genreId, String genreName) {
    public static GenreResponse from(Genre genre) {
        Long genreId = genre.getId();
        String genreNameKo = genre.getGenreNameKo();
        String genreNameEn = genre.getGenreNameEn();

        Locale currentLocale = LocaleContextHolder.getLocale();

        // 추후 언어 추가 가능
        switch (currentLocale.getLanguage()) {
            case "ko":
                return new GenreResponse(genreId, genreNameKo);
            default:
                return new GenreResponse(genreId, genreNameEn);
        }

    }
}
