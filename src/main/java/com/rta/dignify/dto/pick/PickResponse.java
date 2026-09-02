package com.rta.dignify.dto.pick;

import com.rta.dignify.domain.Pick;
import com.rta.dignify.domain.PickTrack;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record PickResponse(
        Long pickId,
        String title,
        String nickname,
        boolean isMine,
        boolean isOfficial,
        Instant createdAt,
        int trackCount,
        int distinctArtistCount,
        String firstArtistName,
        String firstTrackName,
        List<String> thumbnails,
        Map<String, Long> reactions,
        String myReaction,
        /// 상세를 연 횟수. **앱이 적은 값은 숨긴다** — "3번 재생됨"은 아무것도 안 쓰는 것보다 나쁜 신호다.
        int playCount
    ) {

    public static PickResponse of(Pick pick, List<PickTrack> tracks, Map<String, Long> reactions, String myReaction, Long viewerId) {
        int distinctArtistCount = (int) tracks.stream()
                .map(pt -> pt.getTrack().getArtistName())
                .distinct().count();
        Locale currentLocale = LocaleContextHolder.getLocale();

        return new PickResponse(
                pick.getId(),
                pick.getTitle(),
                pick.getUser().getNickname(),
                pick.getUser().getId().equals(viewerId),
                pick.getIsOfficial(),
                pick.getCreatedAt(),
                tracks.size(),
                distinctArtistCount,
                tracks.getFirst().getTrack().displayArtistName(currentLocale),
                tracks.getFirst().getTrack().displayTrackName(currentLocale),
                tracks.stream().limit(3).map(pt -> pt.getTrack().getArtworkUrl()).toList(),
                reactions,
                myReaction,
                pick.getPlayCount()
        );
    }
}

