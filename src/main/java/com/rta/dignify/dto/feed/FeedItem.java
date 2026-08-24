package com.rta.dignify.dto.feed;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/// genreName은 화면 표시용(로케일별), genreNameEn은 로케일과 무관한 고정 키다.
/// 분석에서 "Rock"과 "록"이 다른 값으로 쪼개지지 않으려면 후자를 써야 한다.
public record FeedItem(Long trackId, String trackName, String artistName, String artworkUrl, String previewUrl, String trackViewUrl, String genreName, String genreNameEn, boolean isHyped, SimilarTo similarTo) {
    /// 이 곡이 왜 떴는지 — 유저가 하입한 곡 중 무드가 가장 가까운 것. 무드 정렬로 나온 곡에만 붙고
    /// 콜드스타트·무작위·검색·큐레이션에는 null이다. 앱은 이게 있으면 장르 칩 대신 이걸 띄운다.
    public record SimilarTo(Long trackId, String trackName, String artistName) {
        public static SimilarTo from(Track track) {
            Locale locale = LocaleContextHolder.getLocale();
            return new SimilarTo(track.getId(), track.displayTrackName(locale), track.displayArtistName(locale));
        }
    }

    public static FeedItem from(Track track, boolean isHyped) {
        return from(track, isHyped, null);
    }

    public static FeedItem from(Track track, boolean isHyped, SimilarTo similarTo) {
        Locale locale = LocaleContextHolder.getLocale();
        Genre genre = track.getGenre();
        String genreName = "ko".equals(locale.getLanguage()) ? genre.getGenreNameKo() : genre.getGenreNameEn();
        return new FeedItem(track.getId(), track.displayTrackName(locale), track.displayArtistName(locale), track.getArtworkUrl(), track.getPreviewUrl(), track.displayTrackViewUrl(locale), genreName, genre.getGenreNameEn(), isHyped, similarTo);
    }
}