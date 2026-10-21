package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserHypeTrack;
import com.rta.dignify.dto.hype.HypeGenreCount;
import com.rta.dignify.dto.hype.HypeGenresResponse;
import com.rta.dignify.dto.hype.HypeItem;
import com.rta.dignify.dto.hype.HypeListResponse;
import com.rta.dignify.dto.track.TrackDetailResponse;
import com.rta.dignify.dto.track.TrackHypeUserItem;
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
import java.util.List;

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

        HypeListResponse response1 = hypeService.getMyHypedTracks(user.getId(), null, null);
        assertThat(response1.items()).extracting(HypeItem::trackId)
                .containsExactlyElementsOf(testTrackList.stream().map(Track::getId).toList().subList(testTrackList.size() - 10, testTrackList.size()).reversed());

        HypeListResponse response2 = hypeService.getMyHypedTracks(user.getId(), response1.items().getLast().userHypeTrackId(), null);
        assertThat(response2.items()).extracting(HypeItem::trackId)
                .containsExactlyElementsOf(testTrackList.stream().map(Track::getId).toList().subList(testTrackList.size() - 20, testTrackList.size() - 10).reversed());

        HypeListResponse response3 = hypeService.getMyHypedTracks(user.getId(), response2.items().getLast().userHypeTrackId(), null);
        assertThat(response3.items()).isEmpty();
    }

    @Test
    @DisplayName("좋아요 목록 장르별 수와 장르 필터 테스트")
    void myHypeGenresAndFilterTest() {
        Genre jazz = genreRepository.save(Genre.create("Jazz", "재즈"));
        List<Long> jazzTrackIds = new ArrayList<>();
        // 기본 장르 3곡, 재즈 12곡 — 재즈는 한 페이지(10)를 넘겨 커서 경계를 탄다
        for (int i = 0; i < 15; i++) {
            Genre g = i < 3 ? genre : jazz;
            Track t = trackRepository.save(Track.create("g-" + i, "Artist " + i, "Album " + i, "Track " + i,
                    "https://example.com/p" + i + ".mp3", "https://example.com/t" + i, "https://example.com/a" + i + ".jpg",
                    Instant.now(), g, "US", "ITUNES"));
            userHypeTrackRepository.save(UserHypeTrack.create(user, t));
            if (g == jazz) jazzTrackIds.add(t.getId());
        }

        HypeGenresResponse genres = hypeService.getMyHypeGenres(user.getId());
        assertThat(genres.genres()).extracting(HypeGenreCount::genreId).containsExactly(jazz.getId(), genre.getId());
        assertThat(genres.genres()).extracting(HypeGenreCount::count).containsExactly(12L, 3L);
        assertThat(genres.total()).isEqualTo(15L);

        HypeListResponse page1 = hypeService.getMyHypedTracks(user.getId(), null, jazz.getId());
        assertThat(page1.items()).hasSize(10).allMatch(item -> item.genreId().equals(jazz.getId()));
        HypeListResponse page2 = hypeService.getMyHypedTracks(user.getId(), page1.nextCursor(), jazz.getId());
        assertThat(page2.nextCursor()).isNull();
        assertThat(java.util.stream.Stream.concat(page1.items().stream(), page2.items().stream()).map(HypeItem::trackId))
                .containsExactlyElementsOf(jazzTrackIds.reversed());

        assertThat(hypeService.getMyHypedTracks(user.getId(), null, -1L).items()).isEmpty();
    }

    @Test
    @DisplayName("트랙 상세 조회 테스트")
    void getTrackDetails() {
        Track rockTrack = Track.create("rock-1", "Rock Artist 1", "Rock Album 1", "Rock Track 1",
                "https://example.com/preview/rock1.mp3", "https://example.com/track/rock1", "https://example.com/art/rock1.jpg",
                Instant.now(), genre, "US", "ITUNES");
        trackRepository.save(rockTrack);

        List<User> users = new ArrayList<>();

        for(int i=0; i<10; i++) {
            User user = User.create("test" + i + "@gmail.com", "test" + i);
            userRepository.save(user);

            users.add(user);

            UserHypeTrack userHypeTrack = UserHypeTrack.create(user, rockTrack);
            userHypeTrackRepository.save(userHypeTrack);
        }

        TrackDetailResponse response = hypeService.getTrackDetails(rockTrack.getId());
        assertThat(response.firstHypers()).extracting(TrackHypeUserItem::userId).containsExactlyElementsOf(users.subList(0, 5).stream().map(User::getId).toList());
        assertThat(response.trackId()).isEqualTo(rockTrack.getId());
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
