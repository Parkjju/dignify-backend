package com.rta.dignify.dto.track;

import java.time.Instant;
import java.util.List;

public record TrackDetailResponse(Long trackId, String trackName, String artistName, String collectionName, String artworkUrl, String trackViewUrl, Instant releaseDate, String genreName, List<TrackHypeUserItem> firstHypers) {
}
