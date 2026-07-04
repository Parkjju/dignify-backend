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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 큐레이션(curation_tracks) 우선 노출이 피드 정렬에 반영되는지 검증.
 * 큐레이션 행은 프로덕션과 동일하게 native INSERT로 시딩한다(엔티티 팩토리 없음).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, FeedService.class})
public class CurationFeedTest {

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

    /** 큐레이션 행을 native INSERT로 시딩(프로덕션 시딩과 동일 경로). */
    private void curate(Track track, int priority, boolean active) {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO curation_tracks (track_id, priority, is_active, created_at, updated_at) " +
                        "VALUES (:trackId, :priority, :active, now(), now())")
                .setParameter("trackId", track.getId())
                .setParameter("priority", priority)
                .setParameter("active", active)
                .executeUpdate();
        entityManager.flush();
    }

    @Test
    @DisplayName("큐레이션된 장르 트랙이 GENRE 단계 최상단으로 온다")
    void curatedGenreTrackIsBoostedToTop() {
        entityManager.persistAndFlush(UserGenre.create(user, rockGenre));
        Track boosted = rockTracks.get(10);   // 원래 11번째(track_id 순)
        curate(boosted, 100, true);

        FeedResponse res = feedService.getFeedList(user.getId(), null);

        // 1등 = 큐레이션 트랙(priority로 결정적), 나머지 9개는 seed 셔플이므로 rock 집합만 검증
        List<Long> otherRockIds = rockTracks.stream()
                .map(Track::getId)
                .filter(id -> !id.equals(boosted.getId()))
                .toList();

        assertThat(res.items()).hasSize(10);
        assertThat(res.items().get(0).trackId()).isEqualTo(boosted.getId());
        assertThat(res.items().subList(1, 10)).extracting(FeedItem::trackId).isSubsetOf(otherRockIds);
    }

    @Test
    @DisplayName("선호 장르가 없어도 큐레이션 트랙이 GENERAL 단계 최상단으로 온다")
    void curatedTrackIsBoostedInGeneralPhase() {
        // user에 선호 장르 없음 → 모든 트랙이 general 풀
        Track boosted = countryTracks.get(7);
        curate(boosted, 100, true);

        FeedResponse res = feedService.getFeedList(user.getId(), null);

        assertThat(res.items().get(0).trackId()).isEqualTo(boosted.getId());
        FeedCursor cursor = FeedCursor.decode(res.nextCursor());
        assertThat(cursor.phase()).isEqualTo(FeedCursor.Phase.GENERAL);
    }

    @Test
    @DisplayName("is_active=false 큐레이션은 우선 노출되지 않는다")
    void inactiveCurationIsNotBoosted() {
        entityManager.persistAndFlush(UserGenre.create(user, rockGenre));
        Track inactive = rockTracks.get(10);
        curate(inactive, 100, false);   // 높은 우선순위지만 비활성

        // 낮은 우선순위의 활성 대조 트랙: 비활성 큐레이션이 무시되면 이 트랙이 최상단이어야 함
        Track control = rockTracks.get(0);
        curate(control, 10, true);

        FeedResponse res = feedService.getFeedList(user.getId(), null);

        // inactive(100)가 무시되므로 활성 control(10)이 최상단 (셔플 무관, priority로 결정적)
        assertThat(res.items().get(0).trackId()).isEqualTo(control.getId());
        assertThat(res.items()).hasSize(10);
    }
}
