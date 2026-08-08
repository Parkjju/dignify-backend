package com.rta.dignify.service;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.domain.CurationTrack;
import com.rta.dignify.domain.RequestStatus;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.dto.admin.ArtistRequestItem;
import com.rta.dignify.dto.admin.GenreStat;
import com.rta.dignify.dto.admin.KoBatch;
import com.rta.dignify.dto.admin.PushTargets;
import com.rta.dignify.dto.admin.PushUserItem;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.ArtistRequestRepository;
import com.rta.dignify.repository.CurationTrackRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import com.rta.dignify.service.cron.GenreMapping;
import com.rta.dignify.service.cron.KoEnrichmentBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// 어드민 화면(/internal/admin.html)이 쓰는 조회/편집. 전부 X-Cron-Secret으로만 막힌다.
@RequiredArgsConstructor
@Service
public class AdminService {
    /// 로컬 잡과 같은 크기. iTunes lookup은 id를 한 번에 이만큼까지 받는다.
    private static final int KO_BATCH_SIZE = 190;

    private final CurationTrackRepository curationTrackRepository;
    private final TrackRepository trackRepository;
    private final ArtistRequestRepository artistRequestRepository;
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final ITunesAPIClient iTunesAPIClient;
    private final KoEnrichmentBatchService koEnrichmentBatchService;

    @Transactional(readOnly = true)
    public List<FeedItem> getCurationSet() {
        return curationTrackRepository.findActiveOrdered().stream()
                .map(ct -> FeedItem.from(ct.getTrack(), false))
                .toList();
    }

    /// 받은 목록이 곧 세트다 — 여기 없는 곡은 전부 내려간다. 적은 순서대로 앞에 나온다.
    /// 부분 추가/삭제로 나누지 않는 이유는 화면이 항상 현재 세트를 통째로 들고 편집하기 때문이다.
    @Transactional
    public List<FeedItem> replaceCurationSet(List<Long> trackIds) {
        Map<Long, Track> tracks = trackRepository.findAllById(trackIds).stream()
                .collect(Collectors.toMap(Track::getId, Function.identity()));
        // 없는 id가 섞이면 통째로 막는다. 한 자리 틀린 id가 조용히 빠진 채 세트가 나가면 안 된다.
        if (!tracks.keySet().containsAll(trackIds)) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }

        Map<Long, CurationTrack> existing = curationTrackRepository.findAll().stream()
                .collect(Collectors.toMap(ct -> ct.getTrack().getId(), Function.identity()));
        existing.values().forEach(CurationTrack::deactivate);

        for (int i = 0; i < trackIds.size(); i++) {
            Long trackId = trackIds.get(i);
            int priority = trackIds.size() - i;
            CurationTrack curationTrack = existing.get(trackId);
            if (curationTrack != null) {
                curationTrack.activate(priority);
            } else {
                curationTrackRepository.save(CurationTrack.create(tracks.get(trackId), priority));
            }
        }
        return getCurationSet();
    }

    @Transactional(readOnly = true)
    public List<ArtistRequestItem> getPendingArtistRequests() {
        return artistRequestRepository.findByStatusWithUser(RequestStatus.PENDING).stream()
                .map(ar -> new ArtistRequestItem(ar.getId(), ar.getArtistName(), ar.getUser().getNickname(), ar.getCreatedAt(),
                        trackRepository.countByIsActiveTrueAndArtistNameContainingIgnoreCase(ar.getArtistName())))
                .toList();
    }

    /// 동명이인이 있으면 사람이 골라야 한다. 후보를 그대로 넘기고 화면에서 고르게 한다.
    public List<ItunesItem> searchItunesArtists(String name) {
        return iTunesAPIClient.searchArtists(name.trim());
    }

    /// 장르별 곡 수. 곡이 0인 노출 장르도 0으로 채워 넣는다 — 목록에서 빠지면 얇은 게 아니라
    /// 없는 것처럼 보이고, 매핑이 끊겼을 때(Christian→CCM 같은) 그게 바로 그 증상이다.
    @Transactional(readOnly = true)
    public List<GenreStat> getGenreStats() {
        List<GenreStat> counted = trackRepository.countByGenre();
        Set<String> seen = counted.stream().map(GenreStat::genre).collect(Collectors.toSet());
        return Stream.concat(
                        counted.stream(),
                        GenreMapping.canonicalNames().stream().filter(g -> !seen.contains(g)).map(g -> new GenreStat(g, 0)))
                .sorted(Comparator.comparingLong(GenreStat::tracks).reversed())
                .toList();
    }

    /// 한글 로컬라이즈가 아직 안 붙은 트랙 수. 새로 수집한 곡은 ko_checked=false로 들어온다.
    @Transactional(readOnly = true)
    public long getKoPendingCount() {
        return trackRepository.countByKoCheckedFalse();
    }

    /// 한글 보강 한 배치. 로컬 잡(KoEnrichmentService)은 이걸 큐가 빌 때까지 스스로 반복하는데,
    /// Cloud Run은 요청이 끝나면 CPU를 뺏어서 그 방식이 안 통한다. 그래서 한 배치만 하고 돌려주고,
    /// 반복은 화면이 맡는다 — 매 호출이 정상 요청이라 스로틀링을 안 탄다.
    ///
    /// 트랜잭션을 걸지 않는다. iTunes 호출이 중간에 있고, 배치별 커밋은 안쪽 서비스가 각자 한다.
    public KoBatch enrichKoBatch() {
        List<String> externalIds = koEnrichmentBatchService.peekUncheckedExternalIds(KO_BATCH_SIZE);
        if (externalIds.isEmpty()) {
            return new KoBatch(0, 0, 0);
        }
        int matched = koEnrichmentBatchService.applyKo(externalIds, iTunesAPIClient.lookupKrByTrackIds(externalIds));
        return new KoBatch(externalIds.size(), matched, koEnrichmentBatchService.countUnchecked());
    }

    @Transactional(readOnly = true)
    public PushTargets getPushTargets() {
        List<UserDeviceToken> tokens = userDeviceTokenRepository.findAllWithUser();

        List<PushUserItem> users = tokens.stream()
                .collect(Collectors.groupingBy(t -> t.getUser().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(userTokens -> new PushUserItem(
                        userTokens.get(0).getUser().getId(),
                        userTokens.get(0).getUser().getNickname(),
                        userTokens.size(),
                        userTokens.stream().map(UserDeviceToken::getAppBuild).filter(Objects::nonNull).distinct().sorted().toList(),
                        userTokens.stream().map(UserDeviceToken::getTimeZone).filter(Objects::nonNull).distinct().sorted().toList()))
                .toList();

        // 최신 빌드부터. 빌드 미확인(null)은 맨 뒤 — MIN_BUILD를 걸면 통째로 빠지는 무리라 따로 보여야 한다.
        List<PushTargets.BuildStat> builds = tokens.stream()
                .collect(Collectors.groupingBy(t -> Optional.ofNullable(t.getAppBuild()), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new PushTargets.BuildStat(e.getKey().orElse(null), e.getValue().intValue()))
                .sorted(Comparator.comparing(PushTargets.BuildStat::build, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new PushTargets(users, builds);
    }
}
