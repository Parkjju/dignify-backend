package com.rta.dignify.dto.hype;

import java.util.List;

/// total은 genres의 합이다 — 곡마다 장르가 반드시 있어서(tracks.genre_id NOT NULL) 따로 세지 않는다.
public record HypeGenresResponse(long total, List<HypeGenreCount> genres) {
}
