package com.rta.dignify.dto.genre;

import com.rta.dignify.domain.Genre;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

public record GenreResponse(Long genreId, String genreName, String genreNameEn) {
    public static GenreResponse from(Genre genre) {
        Locale currentLocale = LocaleContextHolder.getLocale();

        // genreName = 표시용(기존 동작 그대로). 지우면 1.0.5가 /users/me 디코딩에 실패해
        // 로그인 유저가 전부 로그아웃된다.
        String genreName = switch (currentLocale.getLanguage()) {
            case "ko" -> genre.getGenreNameKo();
            default -> genre.getGenreNameEn();
        };

        // genreNameEn = 매칭용. 언어와 무관한 고정 키.
        return new GenreResponse(genre.getId(), genreName, genre.getGenreNameEn());
    }
}
