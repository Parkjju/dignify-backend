package com.rta.dignify.repository;

import com.rta.dignify.domain.*;
import com.rta.dignify.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public class TrackRepositoryTest {
    @Autowired
    TestEntityManager entityManager;

    @Autowired
    TrackRepository trackRepository;

    @Test
    @DisplayName("트랙 오프셋 / 리밋 조회")
    void getTracksWithLimitAndOffsetTest() {
        Genre rockGenre = Genre.create("Rock", "락");
        entityManager.persistAndFlush(rockGenre);

        Instant releaseDate = Instant.now();

        List<Track> rockTracks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Track rockTrack = Track.create("rock-" + i, "Rock Artist " + i, "Rock Album " + i, "Rock Track " + i,
                    "https://example.com/preview/rock" + i + ".mp3", "https://example.com/track/rock" + i,
                    "https://example.com/art/rock" + i + ".jpg", releaseDate, rockGenre, "US", "ITUNES");
            entityManager.persistAndFlush(rockTrack);
            rockTracks.add(rockTrack);
        }
        User user = User.create("test@gmail.com", "nickname");
        entityManager.persistAndFlush(user);

        UserGenre userWithRockGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userWithRockGenre);

        // 동일 seed로 순서가 안정적이므로 offset 페이징이 겹치지 않는지 검증(순서 자체는 seed 셔플이라 비결정적)
        List<Track> result1 = trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(user.getId(), 3, 0, 0);
        assertThat(result1).hasSize(3);

        List<Track> result2 = trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(user.getId(), 3, 3, 0);
        assertThat(result2).hasSize(3);

        List<Long> combined = Stream.concat(result1.stream(), result2.stream()).map(Track::getId).toList();
        assertThat(combined).doesNotHaveDuplicates().hasSize(6);
    }

    @Test
    @DisplayName("isActive 트랙 테스트")
    void getOnlyActivatedTracks() {
        Genre rockGenre = Genre.create("Rock", "락");
        entityManager.persistAndFlush(rockGenre);

        Instant releaseDate = Instant.now();

        Track rockTrack1 = Track.create("rock-1", "Rock Artist 1", "Rock Album 1", "Rock Track 1",
                "https://example.com/preview/rock1.mp3", "https://example.com/track/rock1", "https://example.com/art/rock1.jpg",
                releaseDate, rockGenre, "US", "ITUNES");
        Track inactiveTrack = Track.create("rock-2", "Rock Artist 2", "Rock Album 2", "Rock Track 2",
                "https://example.com/preview/rock2.mp3", "https://example.com/track/rock2", "https://example.com/art/rock2.jpg",
                releaseDate, rockGenre, "US", "ITUNES");
        Track rockTrack3 = Track.create("rock-3", "Rock Artist 3", "Rock Album 3", "Rock Track 3",
                "https://example.com/preview/rock3.mp3", "https://example.com/track/rock3", "https://example.com/art/rock3.jpg",
                releaseDate, rockGenre, "US", "ITUNES");

        ReflectionTestUtils.setField(inactiveTrack, "isActive", false);
        entityManager.persistAndFlush(rockTrack1);
        entityManager.persistAndFlush(inactiveTrack);
        entityManager.persistAndFlush(rockTrack3);

        User user = User.create("test@gmail.com", "nickname");
        entityManager.persistAndFlush(user);

        UserGenre userWithRockGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userWithRockGenre);

        List<Track> result = trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(user.getId(), 3, 0, 0);
        assertThat(result).extracting(Track::getId).containsExactlyInAnyOrder(rockTrack1.getId(), rockTrack3.getId());
    }

    @Test
    @DisplayName("선호 장르 필터링 테스트")
    void preferGenreTrackTest() {
        Genre rockGenre = Genre.create("Rock", "락");
        entityManager.persistAndFlush(rockGenre);

        Genre balladGenre = Genre.create("Ballad", "발라드");
        entityManager.persistAndFlush(balladGenre);

        Instant releaseDate = Instant.now();

        for (int i = 1; i <= 10; i++) {
            Track rockTrack = Track.create("rock-" + i, "Rock Artist " + i, "Rock Album " + i, "Rock Track " + i,
                    "https://example.com/preview/rock" + i + ".mp3", "https://example.com/track/rock" + i,
                    "https://example.com/art/rock" + i + ".jpg", releaseDate, rockGenre, "US", "ITUNES");
            entityManager.persistAndFlush(rockTrack);
        }

        for (int i = 1; i <= 10; i++) {
            Track balladTrack = Track.create("ballad-" + i, "ballad Artist " + i, "ballad Album " + i, "ballad Track " + i,
                    "https://example.com/preview/ballad" + i + ".mp3", "https://example.com/track/ballad" + i,
                    "https://example.com/art/ballad" + i + ".jpg", releaseDate, balladGenre, "US", "ITUNES");
            entityManager.persistAndFlush(balladTrack);
        }

        User user = User.create("test@gmail.com", "nickname");
        entityManager.persistAndFlush(user);

        UserGenre userWithRockGenre = UserGenre.create(user, rockGenre);
        entityManager.persistAndFlush(userWithRockGenre);

        // 선호 장르인 Rock 트랙만 10개 조회, 발라드 트랙은 조회되면 안됨
        assertThat(trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(user.getId(), 20, 0, 0)).hasSize(10)
                .extracting(Track::getGenre).extracting(Genre::getGenreNameKo).doesNotContain("발라드");

        // 장르 선호 추가
        UserGenre userWithBalladGenre = UserGenre.create(user, balladGenre);
        entityManager.persistAndFlush(userWithBalladGenre);

        assertThat(trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(user.getId(), 20, 0, 0)).hasSize(20);
    }

    @Test
    @DisplayName("트랙 검색기능 테스트")
    void searchTrackTest() {
        Genre rockGenre = Genre.create("Rock", "락");
        entityManager.persistAndFlush(rockGenre);

        Instant releaseDate = Instant.now();

        List<Track> rockTracks = new ArrayList<>();
        for (int i = 1; i <= 10; i ++) {
            Track rockTrack = Track.create("rock-" + i, "Rock Artist " + i, "Rock Album " + i, "Rock Track " + i,
                    "https://example.com/preview/rock" + i + ".mp3", "https://example.com/track/rock" + i,
                    "https://example.com/art/rock" + i + ".jpg", releaseDate, rockGenre, "US", "ITUNES");
            entityManager.persistAndFlush(rockTrack);
            rockTracks.add(rockTrack);
        }

        for (int i = 11; i <= 20; i ++) {
            Track rockTrack = Track.create("rock-" + i, "search Rock Artist " + i, "Rock Album " + i, "search Rock Track " + i,
                    "https://example.com/preview/rock" + i + ".mp3", "https://example.com/track/rock" + i,
                    "https://example.com/art/rock" + i + ".jpg", releaseDate, rockGenre, "US", "ITUNES");
            entityManager.persistAndFlush(rockTrack);
            rockTracks.add(rockTrack);
        }

        List<Track> searchTracks1 = trackRepository.findTracksWithSearchKeyword("search", 10, 0);
        assertThat(searchTracks1).extracting(Track::getId).containsExactlyElementsOf(rockTracks.subList(10, 20).stream().map(Track::getId).toList());

        List<Track> searchTracks2 = trackRepository.findTracksWithSearchKeyword("search", 10, 10);
        assertThat(searchTracks2).isEmpty();
    }
}
