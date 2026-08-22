package com.rta.dignify.service.cron;

import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/// artistId가 비어 있는 트랙을 iTunes lookup으로 채운다. 트랜잭션 경계만 잡는 역할이고,
/// 배치를 도는 쪽은 AdminService(=어드민 화면)다. KoEnrichmentBatchService와 같은 구조 —
/// iTunes 호출이 트랜잭션 밖에서 일어나도록 읽기와 쓰기를 나눠 둔다.
@RequiredArgsConstructor
@Service
public class ArtistIdBackfillService {
    private final TrackRepository trackRepository;

    /// 커서 이후로 artistId가 빈 트랙들. HTTP 호출은 이 트랜잭션 밖에서 한다.
    @Transactional(readOnly = true)
    public List<Track> peekMissing(long after, int limit) {
        return trackRepository.findByArtistIdIsNullAndIdGreaterThanOrderById(after, Limit.of(limit));
    }

    /// 남은 미채움 수. 커서로 훑는 방식이라 이 값은 0으로 안 떨어질 수 있다 —
    /// iTunes에서 내려간 곡이 그만큼 남는다.
    @Transactional(readOnly = true)
    public long countMissing() {
        return trackRepository.countByArtistIdIsNull();
    }

    /// lookup 결과로 artistId를 채우고 채운 개수를 반환. 매칭 안 된 곡은 그대로 둔다.
    @Transactional
    public int applyArtistIds(List<String> externalIds, List<ItunesItem> items) {
        Map<String, ItunesItem> byExternalId = items.stream()
                .filter(item -> item.trackId() != null && item.artistId() != null)
                .collect(Collectors.toMap(item -> String.valueOf(item.trackId()), Function.identity(), (a, b) -> a));

        int matched = 0;
        for (Track track : trackRepository.findByExternalIdIn(externalIds)) {
            ItunesItem item = byExternalId.get(track.getExternalId());
            if (item != null) {
                track.backfillArtistId(item.artistId());
                matched++;
            }
        }
        return matched;
    }
}
