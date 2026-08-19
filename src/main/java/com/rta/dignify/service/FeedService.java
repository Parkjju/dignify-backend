package com.rta.dignify.service;

import com.rta.dignify.domain.CurationTrack;
import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.feed.CurationResponse;
import com.rta.dignify.dto.feed.FeedCursor;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.repository.CurationTrackRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
@Service
public class FeedService {
    static final Integer FETCH_LIMIT = 10;

    private final TrackRepository trackRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;
    private final CurationTrackRepository curationTrackRepository;

    @Transactional
    public FeedResponse getFeedList(Long userId, String cursorString) {
        List<Track> result;
        FeedResponse response;
        FeedCursor currentCursor;
        FeedCursor newCursor;
        if (cursorString == null) {
            currentCursor = new FeedCursor(FeedCursor.Phase.GENRE, 0, 0, ThreadLocalRandom.current().nextInt());
        } else {
            currentCursor = FeedCursor.decode(cursorString);
        }

        if (currentCursor.phase() == FeedCursor.Phase.GENRE) {
            result = trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(userId, FeedService.FETCH_LIMIT, currentCursor.genreOffset(), currentCursor.seed());
        } else {
            result = new ArrayList<>();
        }

        if (result.size() == FeedService.FETCH_LIMIT) {
            newCursor = new FeedCursor(currentCursor.phase(), currentCursor.genreOffset() + FeedService.FETCH_LIMIT, currentCursor.generalOffset(), currentCursor.seed());
            List<FeedItem> feedItems = result.stream().map((track) -> FeedItem.from(track, false)).toList();
            response = new FeedResponse(feedItems, newCursor.encode(), true, false);
        } else {
            // 장르 조회에서 부족한 결과를 general 조회로 채우기 → 이 페이지는 장르 풀 소진.
            List<Track> paddingResponse = trackRepository.findGeneralTracksByGenreIdsExceptHypedTrackWithLimitAndOffset(userId, FETCH_LIMIT - result.size(), currentCursor.generalOffset(), currentCursor.seed());
            result.addAll(paddingResponse);
            List<FeedItem> feedItems = result.stream().map((track) -> FeedItem.from(track, false)).toList();
            newCursor = new FeedCursor(FeedCursor.Phase.GENERAL, currentCursor.genreOffset() + (FETCH_LIMIT - paddingResponse.size()), currentCursor.generalOffset() + paddingResponse.size(), currentCursor.seed());
            if (result.size() < FeedService.FETCH_LIMIT)  {
                response = new FeedResponse(feedItems, null, false, true);
            } else {
                response = new FeedResponse(feedItems, newCursor.encode(), true, true);
            }
        }
        return response;
    }

    /// 이번 주 큐레이션 세트. 전 유저 동일 내용이고 개인화도 페이징도 없다.
    /// 하입 여부만 유저별로 채워, 이미 담은 곡이 안 담긴 것처럼 보이지 않게 한다.
    @Transactional(readOnly = true)
    public CurationResponse getCurationFeed(Long userId) {
        List<Track> tracks = curationTrackRepository.findActiveOrdered().stream()
                .map(CurationTrack::getTrack).toList();
        List<Long> trackIds = tracks.stream().map(Track::getId).toList();
        Set<Long> hyped = hypedTrackIds(userId, trackIds);
        List<FeedItem> items = tracks.stream()
                .map(track -> FeedItem.from(track, hyped.contains(track.getId())))
                .toList();
        return CurationResponse.of(trackIds, items);
    }

    /// 이 트랙들 중 유저가 하입한 것의 id. 곡마다 따로 묻지 않고 한 번에 받는다.
    ///
    /// 게스트(userId=null)는 하입 자체가 불가능하므로 조회하지 않는다 — 예전엔 곡 수만큼
    /// `existsBy...`를 돌았고, 게스트는 `WHERE user_id = null`이라 항상 false인 걸 열 번 물었다.
    private Set<Long> hypedTrackIds(Long userId, List<Long> trackIds) {
        if (userId == null || trackIds.isEmpty()) {
            return Set.of();
        }
        return userHypeTrackRepository.findHypedTrackIds(userId, trackIds);
    }

    @Transactional(readOnly = true)
    public FeedResponse searchFeedList(Long userId, String cursorString, String searchKeyword) {
        List<Track> result;
        FeedResponse response;
        FeedCursor currentCursor;
        FeedCursor newCursor;
        if (cursorString == null) {
            currentCursor = new FeedCursor(FeedCursor.Phase.GENRE, 0, 0, ThreadLocalRandom.current().nextInt());
        } else {
            currentCursor = FeedCursor.decode(cursorString);
        }

        // DB에 ASCII(')와 커브(’) 따옴표가 섞여 있어서, 어느 쪽으로 쳐도 걸리게 LIKE 단일문자 와일드카드로 치환한다.
        // ponytail: 컬럼 쪽 REPLACE 대신 키워드만 손봄. 정규화 컬럼이 필요해지면 그때 추가.
        String normalizedKeyword = foldAccents(searchKeyword).replaceAll("['‘’ʼ]", "_");
        result = trackRepository.findTracksWithSearchKeyword(normalizedKeyword, FeedService.FETCH_LIMIT, currentCursor.genreOffset());
        Set<Long> hyped = hypedTrackIds(userId, result.stream().map(Track::getId).toList());
        List<FeedItem> feedItems = result.stream()
                .map(track -> FeedItem.from(track, hyped.contains(track.getId())))
                .toList();

        if (result.size() == FeedService.FETCH_LIMIT) {
            newCursor = new FeedCursor(currentCursor.phase(), currentCursor.genreOffset() + FeedService.FETCH_LIMIT, 0, currentCursor.seed());
            response = new FeedResponse(feedItems, newCursor.encode(), true, false);
        } else {
            response = new FeedResponse(feedItems, null, false, false);
        }

        return response;
    }

    /// 쿼리가 컬럼에 거는 LOWER + translate를 검색어에도 똑같이 걸어 "rosalia"가 "ROSALÍA"에 닿게 한다.
    /// 표는 TrackRepository 것을 그대로 써야 양쪽이 어긋나지 않는다.
    private static String foldAccents(String keyword) {
        String lowered = keyword.toLowerCase(Locale.ROOT);
        StringBuilder folded = new StringBuilder(lowered.length());
        for (char c : lowered.toCharArray()) {
            int i = TrackRepository.FOLD_FROM.indexOf(c);
            folded.append(i < 0 ? c : TrackRepository.FOLD_TO.charAt(i));
        }
        return folded.toString();
    }
}
