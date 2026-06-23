package com.rta.dignify.repository;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserHypeTrack;
import com.rta.dignify.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
public class TrackRepositoryTest {
    @Autowired
    TestEntityManager entityManager;

    @Autowired
    TrackRepository trackRepository;

    @Test
    @DisplayName("장르 필터 테스트")
    void genreFilterTest() {
        // 1. 테스트 데이터
        Genre rockGenre = Genre.create("rock", "락");
        Genre balladGenre = Genre.create("ballad", "발라드");

        entityManager.persistAndFlush(rockGenre);
        entityManager.persistAndFlush(balladGenre);

        Instant releaseDate = Instant.now();

        Track rockTrack1 = Track.create("rock-1", "Rock Artist 1", "Rock Album 1", "Rock Track 1",
                "https://example.com/preview/rock1.mp3", "https://example.com/track/rock1", "https://example.com/art/rock1.jpg",
                releaseDate, rockGenre, "US", "ITUNES");
        Track rockTrack2 = Track.create("rock-2", "Rock Artist 2", "Rock Album 2", "Rock Track 2",
                "https://example.com/preview/rock2.mp3", "https://example.com/track/rock2", "https://example.com/art/rock2.jpg",
                releaseDate, rockGenre, "US", "ITUNES");
        Track rockTrack3 = Track.create("rock-3", "Rock Artist 3", "Rock Album 3", "Rock Track 3",
                "https://example.com/preview/rock3.mp3", "https://example.com/track/rock3", "https://example.com/art/rock3.jpg",
                releaseDate, rockGenre, "US", "ITUNES");

        Track balladTrack1 = Track.create("ballad-1", "Ballad Artist 1", "Ballad Album 1", "Ballad Track 1",
                "https://example.com/preview/ballad1.mp3", "https://example.com/track/ballad1", "https://example.com/art/ballad1.jpg",
                releaseDate, balladGenre, "US", "ITUNES");
        Track balladTrack2 = Track.create("ballad-2", "Ballad Artist 2", "Ballad Album 2", "Ballad Track 2",
                "https://example.com/preview/ballad2.mp3", "https://example.com/track/ballad2", "https://example.com/art/ballad2.jpg",
                releaseDate, balladGenre, "US", "ITUNES");
        Track balladTrack3 = Track.create("ballad-3", "Ballad Artist 3", "Ballad Album 3", "Ballad Track 3",
                "https://example.com/preview/ballad3.mp3", "https://example.com/track/ballad3", "https://example.com/art/ballad3.jpg",
                releaseDate, balladGenre, "US", "ITUNES");

        entityManager.persistAndFlush(rockTrack1);
        entityManager.persistAndFlush(rockTrack2);
        entityManager.persistAndFlush(rockTrack3);
        entityManager.persistAndFlush(balladTrack1);
        entityManager.persistAndFlush(balladTrack2);
        entityManager.persistAndFlush(balladTrack3);

        // 2. 복수 장르 필터 / 단일 장르 필터 테스트
        assertThat(trackRepository.findByGenreIdIn(List.of(rockGenre.getId(), balladGenre.getId()))).hasSize(6);
        assertThat(trackRepository.findByGenreIdIn(List.of(rockGenre.getId()))).hasSize(3);
    }

    @Test
    @DisplayName("하입한 트랙 제외 테스트")
    void exceptHypedTracks() {
        // 테스트 데이터
        Genre rockGenre = Genre.create("Rock", "락");
        Genre balladGenre = Genre.create("ballad", "발라드");
        entityManager.persistAndFlush(rockGenre);
        entityManager.persistAndFlush(balladGenre);

        Instant releaseDate = Instant.now();

        Track rockTrack1 = Track.create("rock-1", "Rock Artist 1", "Rock Album 1", "Rock Track 1",
                "https://example.com/preview/rock1.mp3", "https://example.com/track/rock1", "https://example.com/art/rock1.jpg",
                releaseDate, rockGenre, "US", "ITUNES");
        Track rockTrack2 = Track.create("rock-2", "Rock Artist 2", "Rock Album 2", "Rock Track 2",
                "https://example.com/preview/rock2.mp3", "https://example.com/track/rock2", "https://example.com/art/rock2.jpg",
                releaseDate, rockGenre, "US", "ITUNES");
        Track rockTrack3 = Track.create("rock-3", "Rock Artist 3", "Rock Album 3", "Rock Track 3",
                "https://example.com/preview/rock3.mp3", "https://example.com/track/rock3", "https://example.com/art/rock3.jpg",
                releaseDate, rockGenre, "US", "ITUNES");

        Track balladTrack1 = Track.create("ballad-1", "Ballad Artist 1", "Ballad Album 1", "Ballad Track 1",
                "https://example.com/preview/ballad1.mp3", "https://example.com/track/ballad1", "https://example.com/art/ballad1.jpg",
                releaseDate, balladGenre, "US", "ITUNES");
        Track balladTrack2 = Track.create("ballad-2", "Ballad Artist 2", "Ballad Album 2", "Ballad Track 2",
                "https://example.com/preview/ballad2.mp3", "https://example.com/track/ballad2", "https://example.com/art/ballad2.jpg",
                releaseDate, balladGenre, "US", "ITUNES");
        Track balladTrack3 = Track.create("ballad-3", "Ballad Artist 3", "Ballad Album 3", "Ballad Track 3",
                "https://example.com/preview/ballad3.mp3", "https://example.com/track/ballad3", "https://example.com/art/ballad3.jpg",
                releaseDate, balladGenre, "US", "ITUNES");

        entityManager.persistAndFlush(rockTrack1);
        entityManager.persistAndFlush(rockTrack2);
        entityManager.persistAndFlush(rockTrack3);
        entityManager.persistAndFlush(balladTrack1);
        entityManager.persistAndFlush(balladTrack2);
        entityManager.persistAndFlush(balladTrack3);

        User user = User.create("test@gmail.com", "nickname");
        entityManager.persistAndFlush(user);

        User anotherUser = User.create("new@gmail.com", "testNickname");
        entityManager.persistAndFlush(anotherUser);

        UserHypeTrack hypeRockTrack = UserHypeTrack.create(user, rockTrack1);
        entityManager.persistAndFlush(hypeRockTrack);

        UserHypeTrack hypeBalladTrack = UserHypeTrack.create(user, balladTrack3);
        entityManager.persistAndFlush(hypeBalladTrack);

        // 하입 트랙 제외 테스트
        assertThat(trackRepository.findByGenreIdsExceptHypedTrack(user.getId(), List.of(rockGenre.getId(), balladGenre.getId()))).hasSize(4);

        // 하입 등록하지 않은 유저 테스트
        assertThat(trackRepository.findByGenreIdsExceptHypedTrack(anotherUser.getId(), List.of(rockGenre.getId(), balladGenre.getId()))).hasSize(6);
    }

}
