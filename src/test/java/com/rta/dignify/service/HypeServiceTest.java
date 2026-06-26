package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserHypeTrack;
import com.rta.dignify.dto.hype.HypeItem;
import com.rta.dignify.dto.hype.HypeListResponse;
import com.rta.dignify.global.config.JpaAuditingConfig;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
public class HypeServiceTest {
    @Autowired
    GenreRepository genreRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    UserHypeTrackRepository userHypeTrackRepository;

    @Autowired
    HypeService hypeService;

    User user;
    Track track;
    Genre genre;

    @Test
    @DisplayName("하입 등록 테스트")
    void registerHypeTest() {
        Track anotherTrack = Track.create("rock-2", "Rock Artist 2", "Rock Album 2", "Rock Track 2",
                "https://example.com/preview/rock2.mp3", "https://example.com/track/rock2", "https://example.com/art/rock2.jpg",
                Instant.now(), genre, "US", "ITUNES");
        trackRepository.save(anotherTrack);

        UserHypeTrack userHypeTrack = UserHypeTrack.create(user, track);
        userHypeTrackRepository.save(userHypeTrack);

        assertThatThrownBy(() -> hypeService.registerHype(user.getId(), track.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HYPE_ALREADY_REGISTERED);
        assertThatCode(() -> hypeService.registerHype(user.getId(), anotherTrack.getId())).doesNotThrowAnyException();
        assertThat(userHypeTrackRepository.existsByUser_IdAndTrack_Id(user.getId(), anotherTrack.getId())).isTrue();
    }

    @Test
    @DisplayName("하입 삭제 테스트")
    void deleteHypeTest() {
        assertThatThrownBy(() -> hypeService.deleteHype(user.getId(), track.getId())).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HYPE_NOT_FOUND);

        UserHypeTrack userHypeTrack = UserHypeTrack.create(user, track);
        userHypeTrackRepository.save(userHypeTrack);

        assertThatCode(() -> hypeService.deleteHype(user.getId(), track.getId())).doesNotThrowAnyException();
        assertThat(userHypeTrackRepository.existsByUser_IdAndTrack_Id(user.getId(), track.getId())).isFalse();
    }

    @Test
    @DisplayName("하입한 트랙 리스트 테스트")
    void getMyHypedTracksTest() {
        ArrayList<Track> testTrackList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Track testRockTrack = Track.create("rock-" + i, "Rock Artist " + i, "Rock Album " + i, "Rock Track " + i,
                    "https://example.com/preview/rock" + i + ".mp3", "https://example.com/track/rock" + i, "https://example.com/art/rock" + i + ".jpg",
                    Instant.now(), genre, "US", "ITUNES");
            UserHypeTrack userhypeTrack = UserHypeTrack.create(user, testRockTrack);
            testTrackList.add(testRockTrack);

            trackRepository.save(testRockTrack);
            userHypeTrackRepository.save(userhypeTrack);
        }

        HypeListResponse response1 = hypeService.getMyHypedTracks(user.getId(), null);
        assertThat(response1.items()).extracting(HypeItem::trackId)
                .containsExactlyElementsOf(testTrackList.stream().map(Track::getId).toList().subList(testTrackList.size() - 10, testTrackList.size()).reversed());

        HypeListResponse response2 = hypeService.getMyHypedTracks(user.getId(), response1.items().getLast().userHypeTrackId());
        assertThat(response2.items()).extracting(HypeItem::trackId)
                .containsExactlyElementsOf(testTrackList.stream().map(Track::getId).toList().subList(testTrackList.size() - 20, testTrackList.size() - 10).reversed());

        HypeListResponse response3 = hypeService.getMyHypedTracks(user.getId(), response2.items().getLast().userHypeTrackId());
        assertThat(response3.items()).isEmpty();
    }

    @BeforeEach
    void setUp() {
        user = User.create("test@gmail.com", "nickname");
        genre = Genre.create("락", "rock");

        userRepository.save(user);
        genreRepository.save(genre);

        track = Track.create("rock-100", "Rock Artist 100", "Rock Album 100", "Rock Track 100",
                "https://example.com/preview/rock100.mp3", "https://example.com/track/rock100", "https://example.com/art/rock100.jpg",
                Instant.now(), genre, "US", "ITUNES");
        trackRepository.save(track);
    }
}
