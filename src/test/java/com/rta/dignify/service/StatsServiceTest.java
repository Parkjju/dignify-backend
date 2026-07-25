package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.ListenedTrack;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserHypeTrack;
import com.rta.dignify.dto.stats.UserStatsResponse;
import com.rta.dignify.global.config.JpaAuditingConfig;
import com.rta.dignify.repository.ListenedTrackRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * StatsService는 쿼리 결과를 합치는 게 전부라 웹 계층 없이 리포지토리만 띄우고 직접 조립해 검증한다.
 * 피드 쿼리처럼 PostgreSQL 실물이 필요하므로 H2로 대체하지 않는다(TrackRepositoryTest와 동일).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public class StatsServiceTest {
    @Autowired
    TestEntityManager entityManager;

    @Autowired
    ListenedTrackRepository listenedTrackRepository;

    @Autowired
    UserHypeTrackRepository userHypeTrackRepository;

    StatsService statsService;
    User user;
    Genre rock;
    Genre jazz;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(listenedTrackRepository, userHypeTrackRepository);
        user = entityManager.persistAndFlush(User.create("stats@gmail.com", "digger"));
        rock = entityManager.persistAndFlush(Genre.create("Rock", "락"));
        jazz = entityManager.persistAndFlush(Genre.create("Jazz", "재즈"));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("들은 곡 수는 재생 횟수가 아니라 곡 종류 수 — 장르별 개수의 합과 일치")
    void countsDistinctTracksNotPlays() {
        Track rock1 = track("r1", "Rock Artist", rock);
        Track rock2 = track("r2", "Rock Artist", rock);
        Track jazz1 = track("j1", "Jazz Artist", jazz);

        listen(rock1);
        listen(rock1);   // 앱 재시작 후 같은 곡 재청취 → append-only라 row가 하나 더 쌓인다
        listen(rock2);
        listen(jazz1);
        hype(jazz1);

        UserStatsResponse stats = statsService.getMyStats(user.getId(), "all");

        assertThat(stats.distinctListenedCount()).isEqualTo(3);   // 4번 들었지만 곡은 3종
        assertThat(stats.hypeCount()).isEqualTo(1);
        assertThat(stats.listenedByGenre())
                .extracting("genreName", "count")
                .containsExactly(tuple("Rock", 2L), tuple("Jazz", 1L));   // 개수 내림차순
        assertThat(stats.hypedByGenre())
                .extracting("genreName", "count")
                .containsExactly(tuple("Jazz", 1L));
    }

    @Test
    @DisplayName("한글 이름이 일부 곡에만 채워져 있어도 같은 아티스트는 한 줄로 묶인다")
    void groupsArtistByOriginalNameEvenWhenKoIsPartiallyFilled() {
        Track withKo = track("k1", "IU", rock);
        Track withoutKo = track("k2", "IU", rock);
        withKo.applyKoLocalization("아이유", "러브 윈즈 올", "앨범", "https://music.apple.com/kr/album/1");
        entityManager.persistAndFlush(withKo);

        listen(withKo);
        listen(withoutKo);

        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(statsService.getMyStats(user.getId(), "all").listenedByArtist())
                .extracting("artistName", "count")
                .containsExactly(tuple("아이유", 2L));

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(statsService.getMyStats(user.getId(), "all").listenedByArtist())
                .extracting("artistName", "count")
                .containsExactly(tuple("IU", 2L));
    }

    @Test
    @DisplayName("range=week은 최근 7일만 센다")
    void weekRangeCountsLastSevenDaysOnly() {
        Track old = track("o1", "Old Artist", rock);
        Track recent = track("n1", "New Artist", jazz);
        listen(old);
        listen(recent);
        backdate(old, 8);

        assertThat(statsService.getMyStats(user.getId(), "all").distinctListenedCount()).isEqualTo(2);

        UserStatsResponse week = statsService.getMyStats(user.getId(), "week");
        assertThat(week.distinctListenedCount()).isEqualTo(1);
        assertThat(week.listenedByGenre())
                .extracting("genreName")
                .containsExactly("Jazz");
    }

    private Track track(String externalId, String artistName, Genre genre) {
        return entityManager.persistAndFlush(Track.create(externalId, artistName, "Album", "Track " + externalId,
                "https://example.com/preview/" + externalId + ".mp3", "https://example.com/track/" + externalId,
                "https://example.com/art/" + externalId + ".jpg", Instant.now(), genre, "US", "ITUNES"));
    }

    private void listen(Track track) {
        entityManager.persistAndFlush(ListenedTrack.create(user, track));
    }

    private void hype(Track track) {
        entityManager.persistAndFlush(UserHypeTrack.create(user, track));
    }

    /** created_at은 updatable=false라 JPA로 못 바꾼다. 기간 필터를 보려면 SQL로 직접 과거로 돌린다. */
    private void backdate(Track track, int days) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE listened_tracks SET created_at = :when WHERE track_id = :trackId")
                .setParameter("when", Instant.now().minus(days, ChronoUnit.DAYS))
                .setParameter("trackId", track.getId())
                .executeUpdate();
        entityManager.clear();
    }
}
