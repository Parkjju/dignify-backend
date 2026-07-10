package com.rta.dignify.service.cron;

import com.rta.dignify.domain.CronState;
import com.rta.dignify.domain.CurationTrack;
import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.CronStateRepository;
import com.rta.dignify.repository.CurationTrackRepository;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Slf4j
@Service
public class CronBatchService {
    private final GenreRepository genreRepository;
    private final TrackRepository trackRepository;
    private final CronStateRepository cronStateRepository;
    private final CurationTrackRepository curationTrackRepository;
    private final TrackSaveService trackSaveService;

    // iTunes lookup 범위의 시작 id. HTTP 호출은 트랜잭션 밖(CronService)에서 하도록 id만 먼저 내줌.
    @Transactional(readOnly = true)
    public long peekNextStartId(String jobName) {
        CronState cronState = cronStateRepository.findByJobName(jobName).orElseThrow(() -> new BusinessException(ErrorCode.CRON_JOB_NOT_FOUND));
        return cronState.getLastProcessedId() != null ? cronState.getLastProcessedId() + 1 : 1L;
    }

    // 이미 조회된 iTunes 결과만 받아 DB에 적재. 트랜잭션 안에 외부 HTTP 호출 없음.
    @Transactional
    public ProcessResult processBatch(String jobName, List<ItunesItem> itunesItemList) {
        CronState cronState = cronStateRepository.findByJobName(jobName).orElseThrow(() -> new BusinessException(ErrorCode.CRON_JOB_NOT_FOUND));
        long lastProcessedId = cronState.getLastProcessedId() != null
                ? cronState.getLastProcessedId() + 1
                : 1L;

        int saved = saveItems(itunesItemList);
        cronState.updateLastProcessedId(lastProcessedId + 199);
        return new ProcessResult(saved, lastProcessedId + 199);
    }

    // iTunes 결과를 DB에 적재. 중복 제거 + 장르 매핑 + 저장. 저장된 트랙 수 반환. (collect / collect-artist 공용)
    @Transactional
    public int saveItems(List<ItunesItem> itunesItemList) {
        if (itunesItemList.isEmpty()) {
            return 0;
        }
        Set<Long> seenTrackIds = new HashSet<>();
        List<Track> tracks = itunesItemList.stream()
                .filter(itunesItem -> seenTrackIds.add(itunesItem.trackId()))
                .filter(itunesItem -> !trackRepository.existsByExternalIdAndSource(String.valueOf(itunesItem.trackId()), "ITUNES"))
                .flatMap(item -> genreRepository.findByGenreNameEn(item.primaryGenreName())
                        .flatMap(genre -> Track.from(item, genre))
                        .stream())
                .toList();

        tracks.forEach(track -> {
            try {
                trackSaveService.saveTrack(track);
            } catch (DataIntegrityViolationException e) {
                log.warn("Skipping track {}: {}", track.getExternalId(), e.getMessage());
            }
        });
        return tracks.size();
    }

    // 해당 externalId들의 트랙을 curation_tracks에 등록(멱등). collect-artist 전용.
    // saveItems 후 호출 — 이미 저장된 트랙만 조회되므로 정크/장르미매핑 트랙은 자동 제외.
    @Transactional
    public int curateByExternalIds(List<String> externalIds) {
        if (externalIds.isEmpty()) {
            return 0;
        }
        List<Track> tracks = trackRepository.findByExternalIdIn(externalIds);
        int curated = 0;
        for (Track track : tracks) {
            if (!curationTrackRepository.existsByTrack_Id(track.getId())) {
                curationTrackRepository.save(CurationTrack.create(track));
                curated++;
            }
        }
        return curated;
    }

    public record ProcessResult(int processedSize, long lastProcessedId) {}
}
