package com.rta.dignify.dto.feed;

import java.util.List;

// genreExhausted: 이 페이지가 유저 장르 풀을 다 쓰고 장르 무관(GENERAL) 트랙으로 채워졌는지.
public record FeedResponse(List<FeedItem> items, String nextCursor, Boolean hasMore, Boolean genreExhausted) {
}