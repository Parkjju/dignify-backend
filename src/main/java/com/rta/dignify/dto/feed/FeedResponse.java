package com.rta.dignify.dto.feed;

import java.util.List;

public record FeedResponse(List<FeedItem> items, String nextCursor, Boolean hasMore) {
}