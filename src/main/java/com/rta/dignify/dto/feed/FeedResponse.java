package com.rta.dignify.dto.feed;

import com.rta.dignify.domain.Track;

import java.util.List;

public record FeedResponse(List<Track> trackList, String cursor, Boolean hasMore) {
}
