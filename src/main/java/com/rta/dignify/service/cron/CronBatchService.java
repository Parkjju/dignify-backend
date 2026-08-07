package com.rta.dignify.service.cron;

import com.rta.dignify.domain.CronState;
import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.CronStateRepository;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@RequiredArgsConstructor
@Slf4j
@Service
public class CronBatchService {
    private final GenreRepository genreRepository;
    private final TrackRepository trackRepository;
    private final CronStateRepository cronStateRepository;
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

        int saved = saveItems(itunesItemList).saved();
        cronState.updateLastProcessedId(lastProcessedId + 199);
        return new ProcessResult(saved, lastProcessedId + 199);
    }

    // iTunes 결과를 DB에 적재. 중복 제거 + 장르 매핑 + 저장. (collect / collect-artist 공용)
    //
    // 저장 수만 세지 않고 버린 이유까지 세는 건, 'Red Rocks Worship 200곡 중 4곡 저장'처럼 숫자가
    // 작을 때 카탈로그가 원래 작은 건지 매핑이 없어 버려진 건지 구분하려면 그 내역이 있어야 해서다.
    // (실제로 Christian 194곡이 조용히 드롭되고 있었다.)
    @Transactional
    public SaveResult saveItems(List<ItunesItem> itunesItemList) {
        Set<Long> seenTrackIds = new HashSet<>();
        // 매핑에 없어 버린 장르명. 배치당 한 줄로 모아 찍는다 — 아이템마다 찍으면 200줄이 된다.
        Set<String> unmappedGenres = new TreeSet<>();
        List<Track> tracks = new ArrayList<>();
        int duplicate = 0;
        int genreDropped = 0;
        int incomplete = 0;

        for (ItunesItem item : itunesItemList) {
            if (!seenTrackIds.add(item.trackId())
                    || trackRepository.existsByExternalIdAndSource(String.valueOf(item.trackId()), "ITUNES")) {
                duplicate++;
                continue;
            }
            // iTunes 원본 장르로 조회하면 시드된 리프(Singer/Songwriter 등)에 그대로 꽂힌다. GenreMapping 참고.
            String genreName = GenreMapping.canonical(item.primaryGenreName());
            if (genreName == null) {
                // TreeSet은 null을 못 받는다. 장르가 아예 없는 트랙도 실제로 온다.
                unmappedGenres.add(item.primaryGenreName() == null ? "(장르 없음)" : item.primaryGenreName());
                genreDropped++;
                continue;
            }
            // 필수 필드가 비었거나 정크 필터에 걸린 트랙은 Track.from이 비어서 돌아온다.
            Optional<Track> track = genreRepository.findByGenreNameEn(genreName).flatMap(genre -> Track.from(item, genre));
            if (track.isEmpty()) {
                incomplete++;
                continue;
            }
            tracks.add(track.get());
        }

        if (!unmappedGenres.isEmpty()) {
            log.warn("Unmapped genres — 해당 트랙 드롭됨. 남길 장르면 GenreMapping.ALIASES에 추가: {}", unmappedGenres);
        }

        int saved = 0;
        for (Track track : tracks) {
            try {
                trackSaveService.saveTrack(track);
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Skipping track {}: {}", track.getExternalId(), e.getMessage());
                incomplete++;
            }
        }
        return new SaveResult(itunesItemList.size(), saved, duplicate, genreDropped, incomplete, List.copyOf(unmappedGenres));
    }

    public record ProcessResult(int processedSize, long lastProcessedId) {}

    /// 적재 한 번의 내역. found = iTunes가 준 곡 수(미리듣기 있는 것만), 나머지는 왜 안 들어갔는지.
    /// found = saved + duplicate + genreDropped + incomplete 가 항상 성립한다.
    public record SaveResult(int found, int saved, int duplicate, int genreDropped, int incomplete,
                             List<String> unmappedGenres) {
        public static SaveResult empty() {
            return new SaveResult(0, 0, 0, 0, 0, List.of());
        }
    }
}
