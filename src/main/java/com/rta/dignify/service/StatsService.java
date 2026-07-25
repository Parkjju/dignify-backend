package com.rta.dignify.service;

import com.rta.dignify.dto.stats.ArtistCount;
import com.rta.dignify.dto.stats.GenreCount;
import com.rta.dignify.dto.stats.UserStatsResponse;
import com.rta.dignify.repository.ListenedTrackRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RequiredArgsConstructor
@Service
public class StatsService {
    private final ListenedTrackRepository listenedTrackRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;

    @Transactional(readOnly = true)
    public UserStatsResponse getMyStats(Long userId, String range) {
        // 전체 기간은 null 대신 EPOCH. `:since IS NULL` 조건을 쓰면 PostgreSQL이 파라미터 타입을 못 정해 깨진다.
        Instant since = "week".equals(range) ? Instant.now().minus(7, ChronoUnit.DAYS) : Instant.EPOCH;
        boolean ko = "ko".equals(LocaleContextHolder.getLocale().getLanguage());

        List<GenreCount> listenedGenres = listenedTrackRepository.countListenedTracksByGenre(userId, since, ko);
        List<GenreCount> hypedGenres = userHypeTrackRepository.countUserHypeTracksByGenre(userId, since, ko);

        return new UserStatsResponse(range,
                listenedGenres.stream().mapToLong(GenreCount::count).sum(),
                hypedGenres.stream().mapToLong(GenreCount::count).sum(),
                listenedGenres,
                hypedGenres,
                listenedTrackRepository.countListenedTracksByArtist(userId, since, ko),
                userHypeTrackRepository.countUserHypeTracksByArtist(userId, since, ko));
    }

}
