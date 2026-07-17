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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, FeedService.class})
public class FeedServiceTest {

    private static final int TRACKS_PER_GENRE = 15;

    @Autowired
    FeedService feedService;

    @Autowired
    TestEntityManager entityManager;

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
            1. 선호 장르가 없는 유저 조회 테스트
            """)
    void noneOfPreferGenreTest() {
        FeedResponse response = feedService.getFeedList(user.getId(), null);
        FeedCursor cursor = FeedCursor.decode(response.nextCursor());

        assertThat(cursor.phase()).isEqualTo(FeedCursor.Phase.GENERAL);
        assertThat(response.items()).hasSize(10);
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

        // 1. 커서 문자열 null 조회 테스트 (seed 셔플로 순서는 비결정적 → 선호 장르 트랙 집합만 검증)
        FeedResponse response = feedService.getFeedList(user.getId(), null);
        List<Long> allRockIds = rockTracks.stream().map(Track::getId).toList();
        assertThat(response.items()).hasSize(10);
        assertThat(response.items()).extracting(FeedItem::trackId).isSubsetOf(allRockIds);

        // 2. 커서 발급 확인
        String cursorString = response.nextCursor();
        FeedCursor cursor = FeedCursor.decode(cursorString);
        assertThat(cursor.phase()).isEqualTo(FeedCursor.Phase.GENRE);
        assertThat(cursor.generalOffset()).isEqualTo(0);
        assertThat(cursor.genreOffset()).isEqualTo(10);
    }

    @Test
    @DisplayName("""
            1. null 커서 전달, 락 장르 선호
            2. 락 장르 모두 순회 후 GENERAL phase로 전환 테스트
            3. 10개씩 순회되는지, genreOffset / generalOffset값 정상인지 체크
            """)
    void phaseChangingTest() {
        UserGenre userGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userGenre);

        // 1. 커서 문자열 null 조회
        FeedResponse response = feedService.getFeedList(user.getId(), null);

        // 2. 추가순회
        FeedResponse assertResponse = feedService.getFeedList(user.getId(), response.nextCursor());
        List<Long> allRockIds = rockTracks.stream().map(Track::getId).toList();

        // seed 셔플로 페이지별 순서는 비결정적이지만 구조는 유지: page2 앞 5개는 남은 rock, 뒤는 general 패딩
        FeedCursor newCursor = FeedCursor.decode(assertResponse.nextCursor());
        List<FeedItem> trackResponse = assertResponse.items();
        List<Long> generalPoolIds = Stream.concat(balladTracks.stream(), countryTracks.stream())
                .map(Track::getId)
                .toList();

        assertThat(trackResponse.subList(0, 5)).extracting(FeedItem::trackId).isSubsetOf(allRockIds);
        assertThat(trackResponse.subList(5, trackResponse.size())).extracting(FeedItem::trackId).isSubsetOf(generalPoolIds);

        // page1(rock 10개) + page2 앞 5개 = rock 전체, 중복 없음
        List<Long> rockServed = Stream.concat(
                response.items().stream().map(FeedItem::trackId),
                trackResponse.subList(0, 5).stream().map(FeedItem::trackId)
        ).toList();
        assertThat(rockServed).containsExactlyInAnyOrderElementsOf(allRockIds);

        assertThat(newCursor.phase()).isEqualTo(FeedCursor.Phase.GENERAL);
        assertThat(newCursor.genreOffset()).isEqualTo(rockTracks.size());
        assertThat(newCursor.generalOffset()).isEqualTo(5);
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
}
