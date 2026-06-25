package com.rta.dignify.dto.hype;

import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.UserHypeTrack;

import java.time.Instant;

public record HypeItem(Long userHypeTrackId, Long trackId, String trackName, String artistName, String artworkUrl, String previewUrl, Instant hypedAt) {
    public static HypeItem from(UserHypeTrack userHypeTrack) {
        Track track = userHypeTrack.getTrack();
        return new HypeItem(userHypeTrack.getId(), track.getId(), track.getTrackName(), track.getArtistName(), track.getArtworkUrl(), track.getPreviewUrl(), userHypeTrack.getCreatedAt());
    }
}