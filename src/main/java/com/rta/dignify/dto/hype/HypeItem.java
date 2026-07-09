package com.rta.dignify.dto.hype;

import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.UserHypeTrack;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.util.Locale;

public record HypeItem(Long userHypeTrackId, Long trackId, String trackName, String artistName, String artworkUrl, String previewUrl, Instant hypedAt) {
    public static HypeItem from(UserHypeTrack userHypeTrack) {
        Track track = userHypeTrack.getTrack();
        Locale locale = LocaleContextHolder.getLocale();
        return new HypeItem(userHypeTrack.getId(), track.getId(), track.displayTrackName(locale), track.displayArtistName(locale), track.getArtworkUrl(), track.getPreviewUrl(), userHypeTrack.getCreatedAt());
    }
}