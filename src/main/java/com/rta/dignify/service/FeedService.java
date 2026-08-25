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
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
@Slf4j
@Service
public class FeedService {
    /// 한 번에 내려주는 곡 수. **10에서 30으로 올렸다(2026-08-24).**
    /// 무드 스캔 비용은 뽑는 개수가 아니라 벡터 8만 5천 행을 훑는 데 있어서, 30곡을 뽑아도
    /// 10곡과 값이 거의 같다(로컬 실측 23.4ms vs 23.8ms). 요청이 3분의 1로 줄어 유저가
    /// 기다리는 횟수도 서버 부하도 같이 준다. 서울에서 미국 리전까지 왕복이 요청당 218ms라
    /// 이쪽 이득이 스캔 자체보다 클 수 있다.
    static final Integer FETCH_LIMIT = 30;

    private final TrackRepository trackRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;
    private final CurationTrackRepository curationTrackRepository;
    private final MoodRecommender moodRecommender;
    private final ColdStartRecommender coldStartRecommender;
    private final UserRepository userRepository;

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

        // 단계로 갈라 보지 않는다. 예전 앱이 들고 있던 GENERAL 커서도 같은 후보를 봐야 하고,
        // 여기서 빈 결과를 주면 그 유저의 피드가 그 자리에서 끝난다.
        GenrePage page = genreTracks(userId, currentCursor);
        result = page.tracks();
        Map<Long, FeedItem.SimilarTo> similar = page.similar();

        List<FeedItem> feedItems = result.stream().map((track) -> FeedItem.from(track, false, similar.get(track.getId()))).toList();
        // **풀이 하나가 되면서 general 패딩을 없앴다(2026-08-24).** 장르로 안 거르니 장르 단계와
        // 전체 단계가 같은 후보를 보고, 부족한 자리를 전체 단계로 채우면 offset이 0부터 다시
        // 시작해 방금 보여준 곡이 그대로 또 나온다. 이제 카탈로그가 바닥나면 피드가 끝난다.
        //
        // `genreExhausted`는 항상 false다. 앱이 이 값으로 "장르가 소진됐다" 토스트를 띄우는데
        // 장르 선택이 없어진 지금은 띄울 말이 아니다. 응답 필드는 남긴다 — 빼면 앱이 깨진다.
        // **페이지 길이로 소진을 판정하지 않는다(2026-08-24).** 무드 정렬은 스캔 창 안이 전부
        // 걸러지면 짧은 페이지를 내는데, 그건 곡이 없어서가 아니라 창이 좁아서다. 길이로 끊으면
        // 아래에 2만 곡이 남아 있는 유저의 피드가 그 자리에서 닫힌다.
        //
        // 오프셋은 짧은 페이지에도 FETCH_LIMIT만큼 민다. 받은 수만큼만 밀면 다음 요청의 창이
        // 거의 안 커져서 같은 자리를 몇 번씩 오간다. 못 본 곡 몇 개를 건너뛰는 편이 낫다 —
        // 디깅 피드라 2만 8천 곡 중 몇 개는 티가 안 나고, 요청이 헛도는 건 티가 난다.
        if (page.moreBelow()) {
            newCursor = new FeedCursor(currentCursor.phase(), currentCursor.genreOffset() + FeedService.FETCH_LIMIT, currentCursor.generalOffset(), currentCursor.seed());
            response = new FeedResponse(feedItems, newCursor.encode(), true, false);
        } else {
            response = new FeedResponse(feedItems, null, false, false);
        }
        return response;
    }

    /// 유저가 고른 장르 안의 곡을 한 페이지 분량 가져온다.
    ///
    /// 하입이 있으면 무드 유사도 내림차순, 없으면 콜드스타트 인기 풀, 그것도 얇으면 종전 무작위다.
    /// 슬롯도 배지도 토글도 없고 순서만 바뀐다 — 유저가 선언한 장르 밖으로 나가지 않으므로
    /// "피드가 좁아진다"가 구조적으로 막히고, 셋 다 소진되면 그 장르 안 무작위,
    /// 즉 지금 피드가 그대로 최악의 경우다.
    /// 디깅 성향이 켜져 있는지. 게스트는 끌 자리가 없으므로 항상 켜진 것으로 본다.
    /// 유저 행이 없어도 켜짐이다 — 판단이 안 서면 지금 동작을 유지하는 쪽이 안전하다.
    private boolean diggingModeOn(Long userId) {
        if (userId == null) {
            return true;
        }
        return userRepository.findById(userId).map(user -> Boolean.TRUE.equals(user.getDiggingMode())).orElse(true);
    }

    /// 한 페이지의 곡과 "왜 떴는지", 그리고 아래에 더 있는지.
    ///
    /// `moreBelow`를 페이지 길이로 대신할 수 없어서 따로 싣는다. 무드 경로는 스캔 창 안이
    /// 전부 걸러지면 짧은 페이지를 내는데 그건 소진이 아니다. 무작위 폴백은 테이블에서 바로
    /// 뜨므로 짧은 게 곧 소진이고, 콜드스타트는 창을 못 채우면 아예 빈 리스트를 낸다.
    /// 판단 근거가 셋 다 달라서 만든 쪽이 답을 같이 넘긴다.
    private record GenrePage(List<Track> tracks, Map<Long, FeedItem.SimilarTo> similar, boolean moreBelow) {
    }

    private GenrePage genreTracks(Long userId, FeedCursor cursor) {
        // 디깅 성향을 끈 유저는 개인화 두 경로를 통째로 건너뛴다. 콜드스타트도 안 태운다 —
        // 그건 커뮤니티 인기순이라 "제약 없는 무작위"라는 약속과 다르다.
        if (!diggingModeOn(userId)) {
            log.info("[feed] userId={} offset={} source=off size=0", userId, cursor.genreOffset());
            List<Track> random = new ArrayList<>(trackRepository.findRandomTracksExceptHyped(
                    userId, FeedService.FETCH_LIMIT, cursor.genreOffset(), cursor.seed()));
            return new GenrePage(random, Map.of(), random.size() == FETCH_LIMIT);
        }
        List<Long> ordered = moodRecommender.orderedTrackIds(userId, FETCH_LIMIT, cursor.genreOffset());
        String source = "mood";
        // 하입이 없으면(게스트·신규) 무드가 성립하지 않는다. 그 자리를 무작위가 아니라
        // 커뮤니티 인기 풀로 받는다 — 첫 하입이 안 나오면 개인화가 시작되지 않기 때문이다.
        if (ordered.isEmpty()) {
            ordered = coldStartRecommender.orderedTrackIds(userId, FETCH_LIMIT, cursor.genreOffset(), cursor.seed());
            source = "cold";
        }
        // 세 경로가 전부 조용히 서로에게 떨어진다. 어디로 나갔는지 한 줄 남긴다 —
        // 안 남기면 "무드가 도는 건가, 콜드스타트인가, 그냥 무작위인가"를 확인할 방법이 없다.
        log.info("[feed] userId={} offset={} source={} size={}", userId, cursor.genreOffset(),
                ordered.isEmpty() ? "random" : source, ordered.size());
        if (ordered.isEmpty()) {
            List<Track> random = new ArrayList<>(trackRepository.findRandomTracksExceptHyped(
                    userId, FeedService.FETCH_LIMIT, cursor.genreOffset(), cursor.seed()));
            return new GenrePage(random, Map.of(), random.size() == FETCH_LIMIT);
        }
        // id 순서가 곧 정렬 순서다. IN 절 결과는 순서를 보장하지 않으므로 여기서 다시 세운다.
        Map<Long, Track> byId = trackRepository.findAllByIdInFetchGenre(ordered).stream()
                .collect(Collectors.toMap(Track::getId, Function.identity()));
        List<Track> tracks = ordered.stream().map(byId::get).filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        // **페이지 안에서 순서를 섞는다.** 무드 정렬은 시드별 순번으로 내보내서 그대로 두면
        // [A1,B1,C1][A2,B2,C2]... 모양이 된다. 같은 하입 곡이 늘 같은 자리에 서고 유사도가
        // 페이지 내내 한 방향으로만 내려가서, 뒤로 갈수록 힘이 빠지는 느낌이 난다.
        //
        // 어떤 곡이 이 페이지에 들어갈지는 이미 정해졌고 순서만 바꾸므로 시드별 몫(10/10/10)은
        // 그대로다. 커서의 seed와 오프셋으로 씨앗을 만들어 **같은 요청이면 같은 순서**가 나온다 —
        // 매번 다르게 섞으면 같은 페이지를 다시 받았을 때 곡이 겹치거나 빠진다.
        //
        // **앞 SEEDS칸은 안 섞는다.** SQL이 rn 순으로 내보내므로 그 자리는 하입 곡마다 가장 가까운
        // 한 곡씩, 이 페이지에서 제일 좋은 카드다. 전부 섞으면 몇 칸 보고 나가는 유저가 그걸 못 본다.
        // 앞에서 시드 셋이 이미 한 번씩 나오므로 첫인상이 한 곡에 쏠리지도 않는다.
        //
        // 콜드스타트는 안 섞는다. 거기는 반응이 많은 곡이 앞에 서는 게 규칙이고, 그걸 지키려고
        // 그리디 순서를 일부러 되돌려 놓은 자리다(ColdStartRecommender.orderedTrackIds).
        if ("mood".equals(source)) {
            int head = Math.min(MoodRecommender.SEEDS, tracks.size());
            Collections.shuffle(tracks.subList(head, tracks.size()),
                    new Random(cursor.seed() * 31L + cursor.genreOffset()));
        }
        // 콜드스타트는 창(WINDOW)을 못 채우면 빈 리스트를 내므로 길이로 판단해도 맞다.
        boolean moreBelow = "mood".equals(source)
                ? moodRecommender.hasMoreBelow(FETCH_LIMIT, cursor.genreOffset())
                : tracks.size() == FETCH_LIMIT;
        return new GenrePage(tracks, "mood".equals(source) ? similarTo(userId, ordered) : Map.of(), moreBelow);
    }

    /// 무드로 뽑힌 곡마다 "가장 가까운 내 하입 곡"을 붙인다. 콜드스타트·무작위는 부르지 않는다 —
    /// 그 경로엔 시드가 없어서 붙일 근거 자체가 없고, 없는 걸 지어내면 배지가 거짓말이 된다.
    private Map<Long, FeedItem.SimilarTo> similarTo(Long userId, List<Long> trackIds) {
        Map<Long, Long> matches = moodRecommender.seedMatches(userId, trackIds);
        if (matches.isEmpty()) {
            return Map.of();
        }
        Map<Long, Track> seeds = trackRepository.findAllByIdInFetchGenre(matches.values().stream().distinct().toList())
                .stream().collect(Collectors.toMap(Track::getId, Function.identity()));
        return matches.entrySet().stream()
                .filter(e -> seeds.containsKey(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> FeedItem.SimilarTo.from(seeds.get(e.getValue()))));
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
