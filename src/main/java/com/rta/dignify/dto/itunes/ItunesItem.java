package com.rta.dignify.dto.itunes;

public record ItunesItem(
        String wrapperType,
        Long trackId,
        Long artistId,
        String artistName,
        String collectionName,
        String trackName,
        String artworkUrl100,
        String previewUrl,
        String trackViewUrl,
        String artistLinkUrl,   // artist wrapper 전용. 동명이인 후보를 사람이 눈으로 확인할 때 쓴다.
        String primaryGenreName,
        String releaseDate,
        String country
) {}