package com.rta.dignify.dto.feed;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/// genreName은 화면 표시용(로케일별), genreNameEn은 로케일과 무관한 고정 키다.
/// 분석에서 "Rock"과 "록"이 다른 값으로 쪼개지지 않으려면 후자를 써야 한다.
public record FeedItem(Long trackId, String trackName, String artistName, String artworkUrl, String previewUrl, String trackViewUrl, String genreName, String genreNameEn, boolean isHyped) {
    public static FeedItem from(Track track, boolean isHyped) {
        Locale locale = LocaleContextHolder.getLocale();
        Genre genre = track.getGenre();
        String genreName = "ko".equals(locale.getLanguage()) ? genre.getGenreNameKo() : genre.getGenreNameEn();
        return new FeedItem(track.getId(), track.displayTrackName(locale), track.displayArtistName(locale), track.getArtworkUrl(), track.getPreviewUrl(), track.displayTrackViewUrl(locale), genreName, genre.getGenreNameEn(), isHyped);
    }
}