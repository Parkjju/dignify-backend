package com.rta.dignify.dto.feed;

import com.rta.dignify.domain.Track;

public record FeedItem(Long trackId, String trackName, String artistName, String artworkUrl, String previewUrl, String trackViewUrl, boolean isHyped) {
    public static FeedItem from(Track track, boolean isHyped) {
        return new FeedItem(track.getId(), track.getTrackName(), track.getArtistName(), track.getArtworkUrl(), track.getPreviewUrl(), track.getTrackViewUrl(), isHyped);
    }
}