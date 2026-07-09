package com.rta.dignify.dto.track;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.UserHypeTrack;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record TrackDetailResponse(Long trackId, String trackName, String artistName, String collectionName, String artworkUrl, String trackViewUrl, Instant releaseDate, String genreName, List<TrackHypeUserItem> firstHypers) {
    public static TrackDetailResponse from(Track track, List<UserHypeTrack> userHypeTracks) {
        Genre genre = track.getGenre();
        Locale currentLocale = LocaleContextHolder.getLocale();
        String genreName;

        switch (currentLocale.getLanguage()) {
            case "ko":
                genreName = genre.getGenreNameKo();
                break;
            default:
                genreName = genre.getGenreNameEn();
                break;
        }

        List<TrackHypeUserItem> trackHypeUserItems = userHypeTracks.stream().map((userHypeTrack -> new TrackHypeUserItem(userHypeTrack.getUser().getId(), userHypeTrack.getUser().getNickname(), userHypeTrack.getCreatedAt()))).toList();

        return new TrackDetailResponse(
                track.getId(),
                track.displayTrackName(currentLocale),
                track.displayArtistName(currentLocale),
                track.displayCollectionName(currentLocale),
                track.getArtworkUrl(),
                track.displayTrackViewUrl(currentLocale),
                track.getReleaseDate(),
                genreName,
                trackHypeUserItems
        );
    }
}
