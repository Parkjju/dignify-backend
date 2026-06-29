package com.rta.dignify.service;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.domain.CronState;
import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.CronStateRepository;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.LongStream;

@RequiredArgsConstructor
@Service
public class CronBatchService {
    private final TrackRepository trackRepository;
    private final GenreRepository genreRepository;
    private final ITunesAPIClient iTunesAPIClient;
    private final CronStateRepository cronStateRepository;

    @Transactional
    public ProcessResult processBatch(String jobName) {
        CronState cronState = cronStateRepository.findByJobName(jobName).orElseThrow(() -> new BusinessException(ErrorCode.CRON_JOB_NOT_FOUND));
        long lastProcessedId = cronState.getLastProcessedId() != null
                ? cronState.getLastProcessedId() + 1
                : 1L;

        List<ItunesItem> itunesItemList = iTunesAPIClient.lookup(LongStream.range(lastProcessedId, lastProcessedId + 200).boxed().toList());
        List<Track> tracks = List.of();
        if (!itunesItemList.isEmpty()) {
            tracks = itunesItemList.stream()
                    .filter(itunesItem -> {
                        return !trackRepository.existsByExternalIdAndSource(String.valueOf(itunesItem.trackId()), "ITUNES");
                    })
                    .flatMap(item -> genreRepository.findByGenreNameEn(item.primaryGenreName())
                            .map(genre -> Track.from(item, genre))
                            .stream())
                    .toList();
            trackRepository.saveAll(tracks);
        }
        cronState.updateLastProcessedId(lastProcessedId + 199);
        return new ProcessResult(tracks.size(), lastProcessedId + 199);
    }

    public record ProcessResult(int processedSize, long lastProcessedId) {}
}
