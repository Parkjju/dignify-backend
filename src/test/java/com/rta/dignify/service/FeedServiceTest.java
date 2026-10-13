package com.rta.dignify.service;

import com.rta.dignify.domain.*;
import com.rta.dignify.dto.feed.FeedCursor;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, FeedService.class, MoodRecommender.class, ColdStartRecommender.class})
public class FeedServiceTest {

    private static final int TRACKS_PER_GENRE = 15;

    @Autowired
    FeedService feedService;

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    com.rta.dignify.repository.UserHypeTrackRepository userHypeTrackRepository;

    Genre rockGenre;
    Genre balladGenre;
    Genre countryGenre;

    List<Track> rockTracks;
    List<Track> balladTracks;
    List<Track> countryTracks;

    User user;

    @BeforeEach
    void setUp() {
        rockGenre = Genre.create("Rock", "락");
        balladGenre = Genre.create("Ballad", "발라드");
        countryGenre = Genre.create("Country", "컨트리");
        entityManager.persistAndFlush(rockGenre);
        entityManager.persistAndFlush(balladGenre);
        entityManager.persistAndFlush(countryGenre);

        Instant releaseDate = Instant.now();
        rockTracks = createTracks("rock", rockGenre, releaseDate);
        balladTracks = createTracks("ballad", balladGenre, releaseDate);
        countryTracks = createTracks("country", countryGenre, releaseDate);

        user = User.create("test@gmail.com", "nickname");
        entityManager.persistAndFlush(user);
    }

    private List<Track> createTracks(String prefix, Genre genre, Instant releaseDate) {
        List<Track> tracks = new ArrayList<>();
        for (int i = 1; i <= TRACKS_PER_GENRE; i++) {
            Track track = Track.create(prefix + "-" + i, prefix + " Artist " + i, prefix + " Album " + i, prefix + " Track " + i,
                    "https://example.com/preview/" + prefix + i + ".mp3", "https://example.com/track/" + prefix + i,
                    "https://example.com/art/" + prefix + i + ".jpg", releaseDate, genre, "US", "ITUNES");
            entityManager.persistAndFlush(track);
            tracks.add(track);
        }
        return tracks;
    }

    @Test
    @DisplayName("""
            1. 장르를 고르지 않은 유저도 전 카탈로그에서 10곡을 받는다
            """)
    void noneOfPreferGenreTest() {
        FeedResponse response = feedService.getFeedList(user.getId(), null);
        FeedCursor cursor = FeedCursor.decode(response.nextCursor());

        // 예전엔 장르 풀이 비어 GENERAL로 넘어갔다. 이제 장르로 거르지 않으므로 GENRE에 머문다.
        assertThat(cursor.phase()).isEqualTo(FeedCursor.Phase.GENRE);
        assertThat(response.items()).hasSize(FeedService.FETCH_LIMIT);
    }

    @Test
    @DisplayName("""
            1. 커서 문자열 null 케이스 테스트
            2. 커서 발급 확인
            """
    )
    void nullCursorTest() {
        UserGenre userGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userGenre);

        // 1. 커서 문자열 null 조회. **Rock만 고른 유저인데 세 장르가 다 나와야 한다** —
        //    user_genres를 안 읽는 것이 이 테스트가 지키는 성질이다.
        FeedResponse response = feedService.getFeedList(user.getId(), null);
        List<Long> allIds = Stream.of(rockTracks, balladTracks, countryTracks)
                .flatMap(List::stream).map(Track::getId).toList();
        assertThat(response.items()).hasSize(FeedService.FETCH_LIMIT);
        assertThat(response.items()).extracting(FeedItem::trackId).isSubsetOf(allIds);

        // 2. 커서 발급 확인
        String cursorString = response.nextCursor();
        FeedCursor cursor = FeedCursor.decode(cursorString);
        assertThat(cursor.phase()).isEqualTo(FeedCursor.Phase.GENRE);
        assertThat(cursor.generalOffset()).isEqualTo(0);
        assertThat(cursor.genreOffset()).isEqualTo(FeedService.FETCH_LIMIT);
    }

    @Test
    @DisplayName("""
            1. 카탈로그가 바닥나면 피드가 끝난다(general 패딩 없음)
            2. 끝까지 순회해도 같은 곡이 두 번 나오지 않는다
            """)
    void phaseChangingTest() {
        UserGenre userGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userGenre);

        // 장르 필터가 없으니 후보는 세 장르 45곡 전부다. 한 장에 FETCH_LIMIT곡씩 나간다.
        List<Long> drained = new ArrayList<>();
        String cursor = null;
        FeedResponse resp;
        int pages = 0;
        do {
            resp = feedService.getFeedList(user.getId(), cursor);
            resp.items().forEach(item -> drained.add(item.trackId()));
            cursor = resp.nextCursor();
            pages++;
        } while (resp.hasMore());

        int total = TRACKS_PER_GENRE * 3;
        assertThat(pages).isEqualTo((total + FeedService.FETCH_LIMIT - 1) / FeedService.FETCH_LIMIT);
        assertThat(resp.items()).hasSize(total % FeedService.FETCH_LIMIT);
        assertThat(resp.nextCursor()).isNull();
        // 예전엔 부족한 자리를 general 조회로 채웠는데, 두 쿼리가 같아진 지금 그러면
        // offset이 0부터 다시 시작해 방금 본 곡이 또 나온다. 그래서 패딩을 없앴다.
        assertThat(drained).doesNotHaveDuplicates().hasSize(TRACKS_PER_GENRE * 3);
        // 장르 소진 토스트는 더 이상 뜨지 않아야 한다.
        assertThat(resp.genreExhausted()).isFalse();
    }

    @Test
    @DisplayName("""
            1. 멀티 선호 장르 테스트
            2. 전체 순회 시 모든 트랙이 중복 없이 정확히 한 번씩 소진되는지 검증
            """)
    void overallLogicTest() {
        UserGenre userRockGenre = UserGenre.create(user, rockGenre);
        UserGenre userBalladGenre = UserGenre.create(user, balladGenre);
        entityManager.persistAndFlush(userRockGenre);
        entityManager.persistAndFlush(userBalladGenre);

        // seed 셔플로 페이지별 순서/구성은 비결정적 → 전체를 끝까지 순회해 완전성만 검증
        List<Long> drained = new ArrayList<>();
        String cursor = null;
        FeedResponse resp;
        do {
            resp = feedService.getFeedList(user.getId(), cursor);
            resp.items().forEach(item -> drained.add(item.trackId()));
            cursor = resp.nextCursor();
        } while (resp.hasMore());

        List<Long> allIds = Stream.of(rockTracks, balladTracks, countryTracks)
                .flatMap(List::stream)
                .map(Track::getId)
                .toList();

        assertThat(drained).doesNotHaveDuplicates();
        assertThat(drained).containsExactlyInAnyOrderElementsOf(allIds);
        assertThat(resp.hasMore()).isFalse();
        assertThat(resp.nextCursor()).isNull();
    }

    @Test
    @DisplayName("하입한 트랙은 메인 피드에서 제외되어야 한다")
    void hypedTrackExcludedFromFeed() {
        UserGenre userGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userGenre);

        List<Long> hypedIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UserHypeTrack hype = UserHypeTrack.create(user, rockTracks.get(i));
            entityManager.persistAndFlush(hype);
            hypedIds.add(rockTracks.get(i).getId());
        }

        List<Long> drained = new ArrayList<>();
        String cursor = null;
        FeedResponse resp;
        do {
            resp = feedService.getFeedList(user.getId(), cursor);
            resp.items().forEach(item -> drained.add(item.trackId()));
            cursor = resp.nextCursor();
        } while (resp.hasMore());

        assertThat(drained).doesNotContainAnyElementsOf(hypedIds);
    }

    @Test
    @DisplayName("피드 검색 기능 테스트")
    void feedServiceTest() {
        List<Track> testRockTracks = createTracks("search-rock", rockGenre, Instant.now());
        UserHypeTrack userHypeRockTrack = UserHypeTrack.create(user, testRockTracks.get(1));
        entityManager.persistAndFlush(userHypeRockTrack);

        List<Track> testBalladTracks = createTracks("search-ballad", balladGenre, Instant.now());
        UserHypeTrack userHypeBalladTrack = UserHypeTrack.create(user, testBalladTracks.get(1));
        entityManager.persistAndFlush(userHypeBalladTrack);

        testRockTracks.addAll(testBalladTracks);   // 이제 30개(rock+ballad) 전체

        // "search"는 30개를 전부 매칭시키는 합성 쿼리라 여기선 순서가 아니라
        // 완전성/하입 플래그/커서 종료를 검증한다. 관련도 정렬 자체는 TrackRepositoryTest 담당.
        List<Long> drained = new ArrayList<>();
        List<Long> hypedInResult = new ArrayList<>();
        String cursor = null;
        FeedResponse resp;
        do {
            resp = feedService.searchFeedList(user.getId(), cursor, "search");
            resp.items().forEach(item -> {
                drained.add(item.trackId());
                if (item.isHyped()) hypedInResult.add(item.trackId());
            });
            cursor = resp.nextCursor();
        } while (resp.hasMore());

        // 하입한 트랙도 검색엔 나오고 isHyped=true (메인 피드와 달리 제외 안 함)
        assertThat(hypedInResult).containsExactlyInAnyOrder(
                testRockTracks.get(1).getId(), testBalladTracks.get(1).getId());
        // 매칭 30개가 중복·누락 없이 정확히 한 번씩 소진돼야 한다
        assertThat(drained).containsExactlyInAnyOrderElementsOf(
                testRockTracks.stream().map(Track::getId).toList());
    }

    @Test
    @DisplayName("발음기호가 붙은 아티스트를 ASCII로 쳐도 찾는다")
    void searchFoldsAccentsTest() {
        Track rosalia = Track.create("rosalia-1", "ROSALÍA", "El Mal Querer", "MALAMENTE",
                "https://ex/p.mp3", "https://ex/v", "https://ex/a.jpg", Instant.now(), rockGenre, "US", "ITUNES");
        entityManager.persistAndFlush(rosalia);

        // 검색어 쪽(자바)과 컬럼 쪽(SQL translate) 어느 한쪽만 접히면 여기서 깨진다.
        for (String keyword : List.of("rosalia", "ROSALIA", "rosalía")) {
            assertThat(feedService.searchFeedList(user.getId(), null, keyword).items())
                    .extracting(FeedItem::trackId).contains(rosalia.getId());
        }
    }

    @Test
    @DisplayName("""
            1. genreName은 로케일을 따라가고 genreNameEn은 따라가지 않는다
            """)
    void genreNameEnIsLocaleStableTest() {
        UserGenre userGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userGenre);

        // 장르로 거르지 않게 되면서 첫 곡이 록이라는 보장이 사라졌다. 세 장르가 섞여 나오므로
        // 끝까지 훑어 록 곡 하나를 집는다 — 첫 칸을 그냥 쓰면 장르에 따라 결과가 흔들린다.
        LocaleContextHolder.setLocale(Locale.KOREAN);
        FeedItem ko = anyRockItem();
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        FeedItem en = anyRockItem();
        LocaleContextHolder.resetLocaleContext();

        assertThat(ko.genreName()).isEqualTo("락");
        assertThat(en.genreName()).isEqualTo("Rock");
        // 분석 키는 로케일이 바뀌어도 한 값이어야 한다 — 이게 깨지면 장르별 집계가 둘로 쪼개진다
        assertThat(ko.genreNameEn()).isEqualTo("Rock");
        assertThat(en.genreNameEn()).isEqualTo("Rock");
    }

    /// 피드를 끝까지 훑어 록 트랙 한 칸을 돌려준다.
    private FeedItem anyRockItem() {
        List<Long> rockIds = rockTracks.stream().map(Track::getId).toList();
        String cursor = null;
        FeedResponse resp;
        do {
            resp = feedService.getFeedList(user.getId(), cursor);
            for (FeedItem item : resp.items()) {
                if (rockIds.contains(item.trackId())) {
                    return item;
                }
            }
            cursor = resp.nextCursor();
        } while (resp.hasMore());
        throw new AssertionError("피드에 록 트랙이 하나도 없다");
    }

    @Test
    @DisplayName("""
            1. 하입이 없는 유저의 첫 세 페이지는 무작위가 아니라 커뮤니티 인기 풀에서 나온다
            2. 세 페이지가 중복 없이 소진된다
            3. 첫 장은 반응이 가장 많은 10곡이다
            """)
    void coldStartTest() {
        // 다른 유저가 록·발라드 30곡에 반응했다. 컨트리 15곡은 아무도 안 눌렀으므로 풀 밖이다.
        User other = User.create("other@gmail.com", "other");
        entityManager.persistAndFlush(other);
        List<Track> popular = Stream.concat(rockTracks.stream(), balladTracks.stream()).toList();
        for (Track track : popular) {
            entityManager.persistAndFlush(UserHypeTrack.create(other, track));
        }
        // 앞의 10곡만 청취를 더 얹어 점수를 벌린다. 첫 페이지가 이 10곡이어야 한다 —
        // 흩뿌리기 순서를 그대로 내보내면 반응 많은 곡이 3페이지로 밀린다.
        List<Track> mostReacted = popular.subList(0, 10);
        for (Track track : mostReacted) {
            entityManager.persistAndFlush(ListenedTrack.create(other, track));
        }
        int index = 0;
        for (Track track : Stream.concat(popular.stream(), countryTracks.stream()).toList()) {
            insertVector(track, index++);
        }

        // FETCH_LIMIT가 30이 되면서 콜드스타트 창(WINDOW=30)이 첫 한 페이지에 다 들어간다.
        List<Long> firstPage = feedService.getFeedList(user.getId(), null).items()
                .stream().map(FeedItem::trackId).toList();

        assertThat(firstPage).hasSize(ColdStartRecommender.WINDOW);
        // 인기 풀 밖(컨트리)이 섞이면 그냥 무작위로 떨어졌다는 뜻이다.
        assertThat(firstPage).isSubsetOf(popular.stream().map(Track::getId).toList());
        // 앞쪽 열 칸이 반응이 가장 많은 10곡이다. 흩뿌리기는 어느 곡을 담을지만 정하고
        // 순서는 반응 수가 정한다 — 그리디 순서를 그대로 내면 인기곡이 뒤로 밀린다.
        assertThat(firstPage.subList(0, 10))
                .containsExactlyInAnyOrderElementsOf(mostReacted.stream().map(Track::getId).toList());
    }

    @Test
    @DisplayName("""
            1. 추천 기준 곡을 고정하면 최근 하입 대신 그것만 시드가 된다
            2. 디깅 성향을 끄면 개인화 경로를 통째로 건너뛴다
            """)
    void seedPinningAndDiggingModeTest() {
        int index = 0;
        for (Track track : Stream.of(rockTracks, balladTracks, countryTracks).flatMap(List::stream).toList()) {
            insertVector(track, index++);
        }
        // 세 곡을 하입한다. 고정이 없으면 이 셋이 그대로 시드다.
        Track pinned = rockTracks.get(0);
        for (Track track : List.of(pinned, balladTracks.get(0), countryTracks.get(0))) {
            entityManager.persistAndFlush(UserHypeTrack.create(user, track));
        }
        entityManager.flush();

        // 1. 한 곡만 고정하면 모든 카드의 근거가 그 곡이어야 한다.
        //    근거(similarTo)는 시드별 내적의 최댓값을 만든 시드라, 시드가 하나면 전부 그 곡이 된다.
        userHypeTrackRepository.markSeeds(user.getId(), List.of(pinned.getId()));
        entityManager.clear();

        List<FeedItem> items = feedService.getFeedList(user.getId(), null).items();
        assertThat(items).hasSize(FeedService.FETCH_LIMIT);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.similarTo()).isNotNull();
            assertThat(item.similarTo().trackId()).isEqualTo(pinned.getId());
        });

        // 2. 성향을 끄면 무드도 콜드스타트도 안 탄다. 근거가 붙지 않는 것이 그 증거다 —
        //    근거는 무드로 뽑힌 곡에만 붙기 때문이다.
        entityManager.find(User.class, user.getId()).changeDiggingMode(false);
        entityManager.flush();
        entityManager.clear();

        List<FeedItem> off = feedService.getFeedList(user.getId(), null).items();
        assertThat(off).hasSize(FeedService.FETCH_LIMIT);
        assertThat(off).allSatisfy(item -> assertThat(item.similarTo()).isNull());
    }

    @Test
    @DisplayName("""
            1. 같은 커서면 같은 순서가 나온다
            2. 앞 SEEDS칸은 안 섞이고, 뒤쪽만 커서 seed에 따라 달라진다
            """)
    void moodPageIsShuffledButStable() {
        int index = 0;
        for (Track track : Stream.of(rockTracks, balladTracks, countryTracks).flatMap(List::stream).toList()) {
            insertVector(track, index++);
        }
        for (Track track : List.of(rockTracks.get(0), balladTracks.get(0), countryTracks.get(0))) {
            entityManager.persistAndFlush(UserHypeTrack.create(user, track));
        }
        entityManager.clear();

        // 커서를 직접 만든다. null로 넣으면 매번 새 seed가 뽑혀 순서 비교가 성립하지 않는다.
        String cursor = new FeedCursor(FeedCursor.Phase.GENRE, 0, 0, 12345).encode();
        List<Long> first = ids(feedService.getFeedList(user.getId(), cursor));
        resetFeedState();
        List<Long> again = ids(feedService.getFeedList(user.getId(), cursor));

        // 같은 페이지를 같은 커서로 받으면 순서도 같아야 한다. 위치(θ)는 유저에 있어서
        // 다시 받으려면 상태를 지워야 한다.
        assertThat(again).containsExactlyElementsOf(first);

        resetFeedState();
        List<Long> other = ids(feedService.getFeedList(user.getId(),
                new FeedCursor(FeedCursor.Phase.GENRE, 0, 0, 999).encode()));
        // 어떤 곡이 이 페이지에 들어갈지는 점수가 정하고 섞기는 자리만 바꾼다.
        assertThat(other).containsExactlyInAnyOrderElementsOf(first);
        // 앞 칸은 이 페이지에서 가장 가까운 곡들이라 seed와 무관하게 그대로 서 있어야 한다.
        assertThat(other.subList(0, MoodRecommender.SEEDS))
                .containsExactlyElementsOf(first.subList(0, MoodRecommender.SEEDS));
        // 뒤쪽은 실제로 섞였는지. 우연히 같을 확률은 무시할 수 있다.
        assertThat(other).isNotEqualTo(first);
    }

    private static List<Long> ids(FeedResponse response) {
        return response.items().stream().map(FeedItem::trackId).toList();
    }

    private void resetFeedState() {
        entityManager.getEntityManager().createNativeQuery("UPDATE users SET feed_state = NULL").executeUpdate();
        entityManager.clear();
    }

    /// 무드 피드용 곡 n개를 만들고 벡터까지 넣는다.
    private List<Track> bulkTracks(int count) {
        List<Track> bulk = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Track track = Track.create("bulk-" + i, "Bulk Artist " + i, "Bulk Album " + i, "Bulk Track " + i,
                    "https://example.com/preview/bulk" + i + ".mp3", "https://example.com/track/bulk" + i,
                    "https://example.com/art/bulk" + i + ".jpg", Instant.now(), rockGenre, "US", "ITUNES");
            entityManager.persist(track);
            bulk.add(track);
        }
        entityManager.flush();
        for (int i = 0; i < count; i++) {
            insertVector(bulk.get(i), i);
        }
        return bulk;
    }

    @Test
    @DisplayName("""
            1. 커서 없이 다시 불러도 앞 페이지 곡이 다시 나오지 않는다(위치가 유저에 있다)
            2. 시드 하나를 바꿔도 다음 페이지에 이전 곡이 0개다(KAN-23)
            """)
    void moodPagesDoNotRepeatAcrossSeedSwap() {
        List<Track> bulk = bulkTracks(400);
        for (Track track : List.of(bulk.get(0), bulk.get(1), bulk.get(2))) {
            entityManager.persistAndFlush(UserHypeTrack.create(user, track));
        }
        userHypeTrackRepository.markSeeds(user.getId(), List.of(bulk.get(0).getId(), bulk.get(1).getId(), bulk.get(2).getId()));
        entityManager.clear();

        Set<Long> seen = new HashSet<>(ids(feedService.getFeedList(user.getId(), null)));
        // 앱이 커서를 지운 상황(시드 변경 시 iOS가 그렇게 한다)을 null 커서로 흉내 낸다.
        List<Long> second = ids(feedService.getFeedList(user.getId(), null));
        assertThat(second).hasSize(FeedService.FETCH_LIMIT).doesNotContainAnyElementsOf(seen);
        seen.addAll(second);

        // 셋째 시드를 멀리 있는 곡으로 바꾼다. 빠진 시드 덕분에 나갔던 곡이 다시 나오면 안 된다.
        entityManager.persistAndFlush(UserHypeTrack.create(user, entityManager.find(Track.class, bulk.get(200).getId())));
        userHypeTrackRepository.markSeeds(user.getId(), List.of(bulk.get(0).getId(), bulk.get(1).getId(), bulk.get(200).getId()));
        entityManager.clear();

        for (int page = 0; page < 3; page++) {
            List<Long> next = ids(feedService.getFeedList(user.getId(), null));
            assertThat(next).hasSize(FeedService.FETCH_LIMIT).doesNotContainAnyElementsOf(seen);
            seen.addAll(next);
        }
    }

    @Test
    @DisplayName("""
            1. 끝까지 보면 커서를 끊고 상태를 지운다
            2. 꺼진 곡은 트리거로 벡터 쪽에도 꺼져 피드에 안 나온다
            """)
    void moodFeedEndsAndRestarts() {
        List<Track> bulk = bulkTracks(40);
        // 하나를 끈다. tracks만 바꿔도 track_vectors.is_active가 따라가야 한다.
        Track off = bulk.get(5);
        ReflectionTestUtils.setField(off, "isActive", false);
        entityManager.persistAndFlush(off);
        entityManager.persistAndFlush(UserHypeTrack.create(user, bulk.get(0)));
        entityManager.clear();

        List<Long> all = new ArrayList<>();
        FeedResponse response;
        do {
            response = feedService.getFeedList(user.getId(), null);
            all.addAll(ids(response));
        } while (response.hasMore());

        assertThat(response.nextCursor()).isNull();
        assertThat(all).doesNotHaveDuplicates().doesNotContain(off.getId(), bulk.get(0).getId());
        assertThat(entityManager.find(User.class, user.getId()).getFeedState()).isNull();
    }

    /// track_vectors는 엔티티가 없어 네이티브로 넣는다. v0/v1만 쓰고 나머지는 0이라 단위원 위의 점이고,
    /// L2 정규화 상태(내적 = 코사인)라는 운영 데이터의 전제를 그대로 만족한다.
    private void insertVector(Track track, int index) {
        double angle = index * 0.7;
        String columns = IntStream.range(0, MoodRecommender.DIMS).mapToObj(d -> "v" + d).collect(Collectors.joining(","));
        String values = IntStream.range(0, MoodRecommender.DIMS)
                .mapToObj(d -> d == 0 ? String.valueOf(Math.cos(angle)) : d == 1 ? String.valueOf(Math.sin(angle)) : "0")
                .collect(Collectors.joining(","));
        entityManager.getEntityManager().createNativeQuery(
                "INSERT INTO track_vectors (track_id, genre_id, is_active," + columns + ") VALUES ("
                        + track.getId() + "," + track.getGenre().getId() + "," + track.getIsActive() + "," + values + ")").executeUpdate();
    }
}
