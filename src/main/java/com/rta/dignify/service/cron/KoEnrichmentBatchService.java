package com.rta.dignify.service.cron;

import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class KoEnrichmentBatchService {
    private final TrackRepository trackRepository;

    // 아직 KR lookup을 안 시도한 트랙 id들. HTTP 호출은 트랜잭션 밖에서 하도록 id만 내줌.
    @Transactional(readOnly = true)
    public List<String> peekUncheckedExternalIds(int limit) {
        return trackRepository.findUncheckedExternalIds(limit);
    }

    // KR lookup 결과를 받아 매칭되는 row의 ko 컬럼을 채우고, 나머지도 checked 표시. 매칭 개수 반환.
    @Transactional
    public int applyKo(List<String> externalIds, List<ItunesItem> krItems) {
        Map<String, ItunesItem> byExternalId = krItems.stream()
                .filter(item -> item.trackId() != null)
                .collect(Collectors.toMap(item -> String.valueOf(item.trackId()), Function.identity(), (a, b) -> a));

        List<Track> tracks = trackRepository.findByExternalIdIn(externalIds);
        int matched = 0;
        for (Track track : tracks) {
            ItunesItem kr = byExternalId.get(track.getExternalId());
            if (kr != null) {
                track.applyKoLocalization(kr.artistName(), kr.trackName(), kr.collectionName(), kr.trackViewUrl());
                matched++;
            } else {
                track.markKoChecked();
            }
        }
        return matched;
    }
}
