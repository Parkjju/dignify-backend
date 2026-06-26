package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.ListenedTrackRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@Transactional
public class ListenServiceTest {
    @Autowired
    ListenService listenService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GenreRepository genreRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    ListenedTrackRepository listenedTrackRepository;

    User user;
    Track track;

    @Test
    @DisplayName("트랙 청취기록 테스트")
    void recordListenedTrackTest() {
        // 중복 저장 허용
        assertThatCode(() -> listenService.recordListenedTrack(user.getId(), track.getId())).doesNotThrowAnyException();
        assertThatCode(() -> listenService.recordListenedTrack(user.getId(), track.getId())).doesNotThrowAnyException();
        assertThat(listenedTrackRepository.findAll().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 트랙 청취기록 등록 시도")
    void recordListenedTrack_trackNotFound() {
        // 실패 케이스
        assertThatThrownBy(() -> listenService.recordListenedTrack(user.getId(), 9999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TRACK_NOT_FOUND);
    }

    @BeforeEach
    void setUp() {
        user = User.create("test@gmail.com", "nickname");
        userRepository.save(user);

        Genre rockGenre = Genre.create("Rock", "락");
        genreRepository.save(rockGenre);

        track = Track.create("rock-1", "Rock Artist 1", "Rock Album 1", "Rock Track 1",
                "https://example.com/preview/rock1.mp3", "https://example.com/track/rock1", "https://example.com/art/rock1.jpg",
                Instant.now(), rockGenre, "US", "ITUNES");
        trackRepository.save(track);
    }
}
