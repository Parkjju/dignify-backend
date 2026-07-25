package com.rta.dignify.dto.stats;

import java.util.List;

public record UserStatsResponse(
        String range,
        long distinctListenedCount,
        long hypeCount,
        List<GenreCount> listenedByGenre,
        List<GenreCount> hypedByGenre,
        List<ArtistCount> listenedByArtist,
        List<ArtistCount> hypedByArtist
) {
}
