package com.rta.dignify.dto.itunes;

public record ItunesItem(
        String wrapperType,
        Long trackId,
        Long artistId,
        String artistName,
        String trackName,
        String artworkUrl100,
        String previewUrl,
        String trackViewUrl,
        String primaryGenreName,
        String releaseDate
) {}