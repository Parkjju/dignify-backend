package com.rta.dignify.service;

import com.rta.dignify.domain.*;
import com.rta.dignify.dto.feed.CurationResponse;
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
 * 큐레이션(curation_tracks) 동작 검증.
 *
 * 예전엔 큐레이션 곡을 일반 피드 ORDER BY로 최상단에 끌어올렸다. 지금은 /feed/curation
 * 세트가 그 역할을 하고, 일반 피드에서는 아예 빠진다 — 세트에서 한 번 본 곡을 피드 첫 장에서
 * 또 만나면 세트가 새 곡을 준다는 전제가 깨지기 때문이다.
 *
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
        entityManager.clear();
    }

    /** 커서를 끝까지 따라가며 일반 피드가 실제로 내주는 트랙 전부를 모은다. */
    private List<Long> drainFeed() {
        List<Long> drained = new ArrayList<>();
        String cursor = null;
        FeedResponse resp;
        do {
            resp = feedService.getFeedList(user.getId(), cursor);
            resp.items().forEach(item -> drained.add(item.trackId()));
            cursor = resp.nextCursor();
        } while (resp.hasMore());
        return drained;
    }

    @Test
    @DisplayName("세트는 선호 장르를 타지 않고 priority 순으로 나온다")
    void curationSetIgnoresGenreAndOrdersByPriority() {
        entityManager.persistAndFlush(UserGenre.create(user, rockGenre));
        // 선호 장르가 아닌 컨트리 곡 — 장르 필터를 타면 세트에서 빠질 곡이다.
        Track offGenre = countryTracks.get(0);
        Track inGenre = rockTracks.get(0);
        curate(offGenre, 100, true);
        curate(inGenre, 10, true);

        CurationResponse set = feedService.getCurationFeed(user.getId());

        assertThat(set.items()).extracting(FeedItem::trackId)
                .containsExactly(offGenre.getId(), inGenre.getId());
    }

    @Test
    @DisplayName("활성 큐레이션 곡은 GENRE 단계 일반 피드에서 빠진다")
    void curatedTrackIsExcludedFromGenrePhase() {
        entityManager.persistAndFlush(UserGenre.create(user, rockGenre));
        Track curated = rockTracks.get(10);
        curate(curated, 100, true);

        assertThat(drainFeed()).doesNotContain(curated.getId());
    }

    @Test
    @DisplayName("선호 장르가 없어도 큐레이션 곡은 GENERAL 단계에서 빠진다")
    void curatedTrackIsExcludedFromGeneralPhase() {
        // user에 선호 장르 없음 → 모든 트랙이 general 풀
        Track curated = countryTracks.get(7);
        curate(curated, 100, true);

        assertThat(drainFeed()).doesNotContain(curated.getId());
    }

    @Test
    @DisplayName("is_active=false 큐레이션은 세트에도 없고 일반 피드에서 빠지지도 않는다")
    void inactiveCurationIsIgnoredOnBothSides() {
        entityManager.persistAndFlush(UserGenre.create(user, rockGenre));
        Track inactive = rockTracks.get(10);
        curate(inactive, 100, false);   // 높은 우선순위지만 비활성

        assertThat(feedService.getCurationFeed(user.getId()).items()).isEmpty();
        // 지난 주 세트가 피드에서 영구히 사라지면 안 된다 — 교체되면 일반 풀로 돌아와야 한다.
        assertThat(drainFeed()).contains(inactive.getId());
    }

    @Test
    @DisplayName("활성 행이 세트 크기보다 많아도 세트는 상한을 지키고, 넘친 곡은 일반 피드에 남는다")
    void overflowingCurationStaysInTheNormalFeed() {
        // 세트 크기 + 2개를 활성화한다. priority가 낮은 2개는 세트에 못 든다.
        int overflow = FeedService.CURATION_SET_SIZE + 2;
        List<Track> curated = new ArrayList<>(rockTracks.subList(0, overflow));
        for (int i = 0; i < overflow; i++) {
            curate(curated.get(i), overflow - i, true);   // 앞쪽일수록 priority 높음
        }

        List<Long> setIds = feedService.getCurationFeed(user.getId()).items()
                .stream().map(FeedItem::trackId).toList();
        assertThat(setIds).hasSize(FeedService.CURATION_SET_SIZE);

        // 세트에 못 든 2곡이 피드에서도 빠지면 어디에도 안 나오는 곡이 된다.
        List<Long> leftOver = curated.stream().map(Track::getId)
                .filter(id -> !setIds.contains(id)).toList();
        assertThat(leftOver).hasSize(2);
        assertThat(drainFeed()).containsAll(leftOver).doesNotContainAnyElementsOf(setIds);
    }

    @Test
    @DisplayName("setKey는 세트 구성이 그대로면 같고 곡이 바뀌면 달라진다")
    void setKeyChangesOnlyWithComposition() {
        Track a = rockTracks.get(0);
        Track b = countryTracks.get(0);
        curate(a, 20, true);
        curate(b, 10, true);

        String before = feedService.getCurationFeed(user.getId()).setKey();
        assertThat(feedService.getCurationFeed(user.getId()).setKey()).isEqualTo(before);

        entityManager.getEntityManager()
                .createNativeQuery("UPDATE curation_tracks SET is_active = false WHERE track_id = :id")
                .setParameter("id", b.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(feedService.getCurationFeed(user.getId()).setKey()).isNotEqualTo(before);
    }

    @Test
    @DisplayName("하입한 곡이 세트에 있으면 isHyped=true로 나온다")
    void curationSetCarriesHypeState() {
        Track curated = rockTracks.get(0);
        curate(curated, 10, true);
        entityManager.persistAndFlush(UserHypeTrack.create(user, curated));

        assertThat(feedService.getCurationFeed(user.getId()).items())
                .singleElement()
                .extracting(FeedItem::isHyped).isEqualTo(true);
    }
}
