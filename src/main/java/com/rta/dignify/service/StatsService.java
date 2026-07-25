package com.rta.dignify.service;

import com.rta.dignify.dto.stats.GenreCount;
import com.rta.dignify.dto.stats.UserStatsResponse;
import com.rta.dignify.repository.ListenedTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RequiredArgsConstructor
public class StatsService {
    private final ListenedTrackRepository listenedTrackRepository;

    @Transactional(readOnly = true)
    public UserStatsResponse getMyStats(Long userId, String range) {
        Instant since = "week".equals(range) ? Instant.now().minus(7, ChronoUnit.DAYS) : null;
        boolean ko = "ko".equals(LocaleContextHolder.getLocale().getLanguage());

        List<GenreCount> listenedGenres =
    }

}
