package com.rta.dignify.service;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.domain.CurationTrack;
import com.rta.dignify.domain.RequestStatus;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.dto.admin.ArtistRequestItem;
import com.rta.dignify.dto.admin.GenreStat;
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
    private final CurationTrackRepository curationTrackRepository;
    private final TrackRepository trackRepository;
    private final ArtistRequestRepository artistRequestRepository;
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final ITunesAPIClient iTunesAPIClient;

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

    @Transactional(readOnly = true)
    public List<PushUserItem> getPushUsers() {
        Map<Long, List<UserDeviceToken>> byUser = userDeviceTokenRepository.findAllWithUser().stream()
                .collect(Collectors.groupingBy(t -> t.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

        return byUser.values().stream()
                .map(tokens -> new PushUserItem(
                        tokens.get(0).getUser().getId(),
                        tokens.get(0).getUser().getNickname(),
                        tokens.size(),
                        tokens.stream().map(UserDeviceToken::getAppBuild).filter(Objects::nonNull).distinct().sorted().toList(),
                        tokens.stream().map(UserDeviceToken::getTimeZone).filter(Objects::nonNull).distinct().sorted().toList()))
                .toList();
    }
}
