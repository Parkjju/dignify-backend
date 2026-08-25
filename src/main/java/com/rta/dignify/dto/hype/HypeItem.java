package com.rta.dignify.dto.hype;

import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.UserHypeTrack;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.util.Locale;

/// isSeed는 추천 기준으로 고정한 곡인지다. 시드 선택 화면이 현재 선택을 그려야 해서 같이 내려간다.
public record HypeItem(Long userHypeTrackId, Long trackId, String trackName, String artistName, String artworkUrl, String previewUrl, Instant hypedAt, boolean isSeed) {
    public static HypeItem from(UserHypeTrack userHypeTrack) {
        Track track = userHypeTrack.getTrack();
        Locale locale = LocaleContextHolder.getLocale();
        return new HypeItem(userHypeTrack.getId(), track.getId(), track.displayTrackName(locale), track.displayArtistName(locale), track.getArtworkUrl(), track.getPreviewUrl(), userHypeTrack.getCreatedAt(), Boolean.TRUE.equals(userHypeTrack.getIsSeed()));
    }
}