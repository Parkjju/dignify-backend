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

        // 1. 커서 문자열 null 조회 테스트
        FeedResponse response = feedService.getFeedList(user.getId(), null);
        List<Long> expectedIds = rockTracks.subList(0, 10).stream().map(Track::getId).toList();
        assertThat(response.items()).extracting(FeedItem::trackId).containsExactlyElementsOf(expectedIds);

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
        List<Long> expectedIdsInRock = rockTracks.subList(10, rockTracks.size()).stream().map(Track::getId).toList();

        // NOT IN UserGenre이므로 paddingTrack에 어떤 장르의 트랙이 들어갈지는 테스트 불필요
        // 전체 갯수만 체크
        FeedCursor newCursor = FeedCursor.decode(assertResponse.nextCursor());
        List<FeedItem> trackResponse = assertResponse.items();
        List<Long> generalPoolIds = Stream.concat(balladTracks.stream(), countryTracks.stream())
                .map(Track::getId)
                .toList();

        assertThat(trackResponse.subList(0, 5)).extracting(FeedItem::trackId).containsExactlyElementsOf(expectedIdsInRock);
        assertThat(trackResponse.subList(5, trackResponse.size())).extracting(FeedItem::trackId).isSubsetOf(generalPoolIds);
        assertThat(newCursor.phase()).isEqualTo(FeedCursor.Phase.GENERAL);
        assertThat(newCursor.genreOffset()).isEqualTo(rockTracks.size());
        assertThat(newCursor.generalOffset()).isEqualTo(5);
    }

    @Test
    @DisplayName("""
            1. 멀티 선호 장르 테스트
            2. 모든 트랙 조회 및 고갈 테스트
            """)
    void overallLogicTest() {
        UserGenre userRockGenre = UserGenre.create(user, rockGenre);
        UserGenre userBalladGenre = UserGenre.create(user, balladGenre);
        entityManager.persistAndFlush(userRockGenre);
        entityManager.persistAndFlush(userBalladGenre);

        // 1. 커서 문자열 null 조회
        FeedResponse fetch1 = feedService.getFeedList(user.getId(), null);
        FeedCursor cursor = FeedCursor.decode(fetch1.nextCursor());
        assertThat(cursor.genreOffset()).isEqualTo(10);

        // 2. 1차 순회
        FeedResponse fetch2 = feedService.getFeedList(user.getId(), fetch1.nextCursor());
        List<Long> expectedIdsInRock = rockTracks.subList(10, rockTracks.size()).stream().map(Track::getId).toList();
        List<Long> expectedIdsInBallad = balladTracks.subList(0, 5).stream().map(Track::getId).toList();

        assertThat(fetch2.items().subList(0, 5)).extracting(FeedItem::trackId).containsExactlyElementsOf(expectedIdsInRock);
        assertThat(fetch2.items().subList(5, fetch2.items().size())).extracting(FeedItem::trackId).containsExactlyElementsOf(expectedIdsInBallad);

        // 3. 2차 순회, 선호장르 전부 소진
        FeedResponse fetch3 = feedService.getFeedList(user.getId(), fetch2.nextCursor());
        FeedCursor fetch3Cursor = FeedCursor.decode(fetch3.nextCursor());
        List<Long> expectedIdsInBalladLast = balladTracks.subList(5, balladTracks.size()).stream().map(Track::getId).toList();

        assertThat(fetch3.items()).extracting(FeedItem::trackId).containsExactlyElementsOf(expectedIdsInBalladLast);
        assertThat(fetch3Cursor.genreOffset()).isEqualTo(rockTracks.size() + balladTracks.size());
        assertThat(fetch3Cursor.phase()).isEqualTo(FeedCursor.Phase.GENRE); // 꽉맞게 소진할때까지는 GENRE Phase 유지

        // 4. 3차 순회, GENERAL 순회
        FeedResponse fetch4 = feedService.getFeedList(user.getId(), fetch3.nextCursor());
        FeedCursor fetch4Cursor = FeedCursor.decode(fetch4.nextCursor());
        List<Long> expectedIdsInCountry = countryTracks.subList(0, 10).stream().map(Track::getId).toList();

        assertThat(fetch4.items()).extracting(FeedItem::trackId).containsExactlyElementsOf(expectedIdsInCountry);
        assertThat(fetch4Cursor.phase()).isEqualTo(FeedCursor.Phase.GENERAL);

        // 5. 최종 순회
        // - 5개만 최종 결과 반환
        // - cursor값 null
        // - hasMore false
        FeedResponse fetch5 = feedService.getFeedList(user.getId(), fetch4.nextCursor());
        List<Long> expectedIdsInCountryLast = countryTracks.subList(10, countryTracks.size()).stream().map(Track::getId).toList();

        assertThat(fetch5.items()).extracting(FeedItem::trackId).containsExactlyElementsOf(expectedIdsInCountryLast);
        assertThat(fetch5.nextCursor()).isNull();
        assertThat(fetch5.hasMore()).isFalse();
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

        testRockTracks.addAll(testBalladTracks);

        // 1. 커서 문자열 null 조회
        FeedResponse fetch1 = feedService.searchFeedList(user.getId(), null, "search");
        assertThat(fetch1.items().get(1).isHyped()).isTrue();
        assertThat(fetch1.items()).extracting(FeedItem::trackId).containsExactlyElementsOf(testRockTracks.subList(0, 10).stream().map(Track::getId).toList());

        // 2. 1차 순회
        FeedResponse fetch2 = feedService.searchFeedList(user.getId(), fetch1.nextCursor(), "search");
        assertThat(fetch2.items().get(6).isHyped()).isTrue();
        assertThat(fetch2.items()).extracting(FeedItem::trackId).containsExactlyElementsOf(Stream.concat(testRockTracks.subList(10, 15).stream(), testBalladTracks.subList(0, 5).stream()).map(Track::getId).toList());

        // 3. 2차 순회
        FeedResponse fetch3 = feedService.searchFeedList(user.getId(), fetch2.nextCursor(), "search");
        assertThat(fetch3.items()).extracting(FeedItem::isHyped).doesNotContain(true);
        assertThat(fetch3.items()).extracting(FeedItem::trackId).containsExactlyElementsOf(testBalladTracks.subList(5, 15).stream().map(Track::getId).toList());
        assertThat(fetch3.hasMore()).isTrue();

        // 최종 순회
        FeedResponse fetch4 = feedService.searchFeedList(user.getId(), fetch3.nextCursor(), "search");
        assertThat(fetch4.items()).isEmpty();
        assertThat(fetch4.hasMore()).isFalse();
    }
}
