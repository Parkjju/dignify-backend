package com.rta.dignify.service;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.domain.*;
import com.rta.dignify.global.config.ClockConfig;
import com.rta.dignify.global.config.JpaAuditingConfig;
import com.rta.dignify.repository.PushLogRepository;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import com.rta.dignify.service.cron.CronBatchService;
import com.rta.dignify.service.cron.KoEnrichmentBatchService;
import com.rta.dignify.service.cron.TrackSaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// 재방문 푸시 2종(신곡 알림·주간 추천)과 광고성 동의. 발송 자체(APNs/FCM)는 테스트 환경에 빈이 없어 빼고,
/// 누구에게 무엇을 보낼지 정하는 쪽만 본다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ClockConfig.class, MoodRecommender.class, NewReleaseService.class, WeeklyPickService.class,
        UserService.class, ITunesAPIClient.class, CronBatchService.class, TrackSaveService.class, KoEnrichmentBatchService.class})
class RevisitPushTest {
    @Autowired TestEntityManager em;
    @Autowired NewReleaseService newReleaseService;
    @Autowired WeeklyPickService weeklyPickService;
    @Autowired MoodRecommender moodRecommender;
    @Autowired UserService userService;
    @Autowired UserDeviceTokenRepository tokenRepository;
    @Autowired PushLogRepository pushLogRepository;

    Genre genre;
    User user;
    int seq;

    @BeforeEach
    void setUp() {
        genre = em.persistAndFlush(Genre.create("Rock", "락"));
        user = em.persistAndFlush(User.create("a@test.com", "digger"));
    }

    private Track track(long artistId, String name, Instant released) {
        Track t = Track.create("ext-" + (++seq), "Artist " + artistId, "Album", name, "p", "v", "a", released, genre, "US", "ITUNES");
        ReflectionTestUtils.setField(t, "artistId", artistId);
        return em.persistAndFlush(t);
    }

    private void createdAt(Track t, Instant at) {
        em.getEntityManager().createNativeQuery("UPDATE tracks SET created_at = ?1 WHERE track_id = ?2")
                .setParameter(1, at).setParameter(2, t.getId()).executeUpdate();
    }

    private void vector(Track t, double angle) {
        String cols = IntStream.range(0, MoodRecommender.DIMS).mapToObj(d -> "v" + d).collect(Collectors.joining(","));
        String vals = IntStream.range(0, MoodRecommender.DIMS)
                .mapToObj(d -> d == 0 ? String.valueOf(Math.cos(angle)) : d == 1 ? String.valueOf(Math.sin(angle)) : "0")
                .collect(Collectors.joining(","));
        em.getEntityManager().createNativeQuery("INSERT INTO track_vectors (track_id, genre_id, is_active," + cols + ") VALUES ("
                + t.getId() + "," + genre.getId() + ",true," + vals + ")").executeUpdate();
    }

    @Test
    @DisplayName("신곡 — 재발매·같은 실행 안의 같은 곡·발매 7일 밖은 알림에서 빠진다")
    void newReleaseFiltering() {
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofHours(1));
        Track old = track(1, "Song", now.minus(Duration.ofDays(400)));
        createdAt(old, now.minus(Duration.ofDays(30)));
        track(1, "Song (Remastered 2026)", now.minus(Duration.ofDays(1)));     // 재발매
        Track fresh = track(1, "Fresh", now.minus(Duration.ofDays(2)));
        track(1, "Fresh - Deluxe Edition", now.minus(Duration.ofDays(2)));     // 같은 실행 안의 같은 곡
        track(2, "Stale", now.minus(Duration.ofDays(10)));                     // 발매 7일 밖
        em.clear();

        assertThat(newReleaseService.newReleasesSince(since, Instant.now())).extracting(Track::getId).containsExactly(fresh.getId());
    }

    @Test
    @DisplayName("신곡 — 발매일 판정: 미래(선주문)와 7일 밖은 아니다")
    void recentRelease() {
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        assertThat(NewReleaseService.isRecentRelease("2026-10-25T07:00:00Z", now)).isTrue();
        assertThat(NewReleaseService.isRecentRelease("2026-11-01T07:00:00Z", now)).isFalse();
        assertThat(NewReleaseService.isRecentRelease("2026-10-10T07:00:00Z", now)).isFalse();
        assertThat(NewReleaseService.isRecentRelease(null, now)).isFalse();
    }

    @Test
    @DisplayName("신곡 — 오늘 pm 이력이 있으면 pm 재실행은 아무것도 안 한다")
    void pmRerunSkipped() throws Exception {
        track(1, "Fresh", Instant.now().minus(Duration.ofDays(1)));
        assertThat(newReleaseService.run(PushVariant.Slot.PM).newReleases()).isEqualTo(1);

        pushLogRepository.saveAndFlush(PushLog.start("new_release_pm", "new_release", "t", "b", null, null, false));
        assertThat(newReleaseService.run(PushVariant.Slot.PM).newReleases()).isZero();
    }

    @Test
    @DisplayName("신곡 피드 — 좋아요한 아티스트의 안 들은 신곡만, 동의와 무관")
    void newReleaseFeed() {
        Track liked = track(1, "Old Hit", Instant.now().minus(Duration.ofDays(500)));
        createdAt(liked, Instant.now().minus(Duration.ofDays(60)));
        em.persistAndFlush(UserHypeTrack.create(user, liked));
        Track fresh = track(1, "New One", Instant.now().minus(Duration.ofDays(1)));
        Track heard = track(1, "Heard", Instant.now().minus(Duration.ofDays(1)));
        em.persistAndFlush(ListenedTrack.create(user, heard));
        track(2, "Someone Else", Instant.now().minus(Duration.ofDays(1)));
        em.clear();

        assertThat(user.getAdPushConsent()).isFalse();
        assertThat(newReleaseService.feedTrackIds(user.getId())).containsExactly(fresh.getId());
        assertThat(newReleaseService.feedTrackIds(null)).isEmpty();
    }

    @Test
    @DisplayName("similarTo — 들은 곡·하입 곡을 빼고, 평소 피드의 진행 위치(feed_state)를 안 바꾼다")
    void similarToKeepsFeedState() {
        FeedState state = new FeedState(List.of(999L), List.of(999L), 0.5, List.of());
        user.updateFeedState(state);
        em.persistAndFlush(user);
        Track seed = track(1, "Seed", Instant.now());
        vector(seed, 0);
        em.persistAndFlush(UserHypeTrack.create(user, seed));
        List<Track> others = IntStream.range(1, 6).mapToObj(i -> track(10 + i, "T" + i, Instant.now())).toList();
        for (int i = 0; i < others.size(); i++) vector(others.get(i), 0.4 + i * 0.3);
        em.persistAndFlush(ListenedTrack.create(user, others.get(0)));
        em.clear();

        List<Long> ids = moodRecommender.similarTo(List.of(seed.getId()), user.getId(), 3);

        assertThat(ids).containsExactly(others.get(1).getId(), others.get(2).getId(), others.get(3).getId());
        em.clear();
        assertThat(em.find(User.class, user.getId()).getFeedState()).isEqualTo(state);
        // 게스트도 된다(제외 없이 정렬만).
        assertThat(moodRecommender.similarTo(List.of(seed.getId()), null, 2)).hasSize(2);
    }

    @Test
    @DisplayName("주간 추천 — 기준 곡은 최근 3곡·이미 쓴 곡을 빼고, 후보가 없으면 대기, 같은 주엔 한 번")
    void weeklyPickSeedAndUnique() {
        List<Track> hyped = IntStream.range(0, 4).mapToObj(i -> track(i + 1, "H" + i, Instant.now())).toList();
        for (int i = 0; i < hyped.size(); i++) {
            vector(hyped.get(i), i * 0.05);
            em.persistAndFlush(UserHypeTrack.create(user, hyped.get(i)));
        }
        IntStream.range(0, 8).forEach(i -> vector(track(100 + i / 3, "R" + i, Instant.now()), 1.0 + i * 0.1));
        em.clear();

        // 4곡 중 최근 3곡은 평소 피드의 시드라 빠지고 가장 오래된 하나만 남는다.
        assertThat(weeklyPickService.seedCandidate(user.getId())).contains(hyped.get(0).getId());

        LocalDate week = LocalDate.of(2026, 10, 26);
        WeeklyPick pick = weeklyPickService.pick(user.getId(), week, "push-1").orElseThrow();
        assertThat(pick.getSeedTrack().getId()).isEqualTo(hyped.get(0).getId());
        assertThat(pick.getTrackIds()).hasSize(5).doesNotContainAnyElementsOf(hyped.stream().map(Track::getId).toList());

        assertThat(weeklyPickService.pick(user.getId(), week, "push-2")).isEmpty();               // 같은 주
        assertThat(weeklyPickService.pick(user.getId(), week.plusWeeks(1), "push-3")).isEmpty();  // 후보 소진 → 대기
    }

    @Test
    @DisplayName("주간 추천 — 유사도 순서를 지키며 아티스트당 2곡, 5곡까지")
    void capPerArtist() {
        Map<Long, String> artist = Map.of(1L, "a", 2L, "a", 3L, "a", 4L, "b", 5L, "c", 6L, "c", 7L, "c", 8L, "d");
        assertThat(WeeklyPickService.capPerArtist(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), artist::get, 2, 5))
                .containsExactly(1L, 2L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("주간 추천 대상 — 좋아요 4곡 이상 + 광고성 동의 + 그룹 일치")
    void weeklyTargets() {
        IntStream.range(0, 4).forEach(i -> em.persistAndFlush(UserHypeTrack.create(user, track(i + 1, "H" + i, Instant.now()))));
        PushVariant group = PushVariant.of(user.getId());
        assertThat(weeklyPickService.targets(group)).isEmpty();   // 동의 전

        user.changeAdPushConsent(true, Instant.now());
        em.persistAndFlush(user);
        assertThat(weeklyPickService.targets(group)).containsExactly(user.getId());
        assertThat(weeklyPickService.targets(group == PushVariant.A ? PushVariant.B : PushVariant.A)).isEmpty();
    }

    @Test
    @DisplayName("광고성 발송 대상은 동의한 유저의 기기뿐 / 수신거부는 기기 토큰만으로 동의를 거둔다")
    void consentFilterAndOptOut() {
        User other = em.persistAndFlush(User.create("b@test.com", "other"));
        em.persistAndFlush(UserDeviceToken.create(user, "tok-a", "production", null, "Asia/Seoul", 30));
        em.persistAndFlush(UserDeviceToken.create(other, "tok-b", "production", null, "Asia/Seoul", 30));
        userService.changeAdPushConsent(user.getId(), true);
        em.flush();
        em.clear();

        assertThat(tokenRepository.findAdConsented()).extracting(UserDeviceToken::getToken).containsExactly("tok-a");

        var response = userService.optOutByDeviceToken("tok-a");
        em.flush();
        em.clear();
        assertThat(response.adPush()).isFalse();
        assertThat(response.processedAt()).isNotNull();
        assertThat(tokenRepository.findAdConsented()).isEmpty();
    }
}
