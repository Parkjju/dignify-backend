package com.rta.dignify.dto.feed;

import com.rta.dignify.domain.Track;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

public record FeedItem(Long trackId, String trackName, String artistName, String artworkUrl, String previewUrl, String trackViewUrl, boolean isHyped) {
    public static FeedItem from(Track track, boolean isHyped) {
        Locale locale = LocaleContextHolder.getLocale();
        return new FeedItem(track.getId(), track.displayTrackName(locale), track.displayArtistName(locale), track.getArtworkUrl(), track.getPreviewUrl(), track.displayTrackViewUrl(locale), isHyped);
    }
}