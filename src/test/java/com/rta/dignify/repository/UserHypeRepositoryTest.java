package com.rta.dignify.repository;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserHypeTrack;
import com.rta.dignify.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public class UserHypeRepositoryTest {
    @Autowired
    TestEntityManager entityManager;

    @Autowired
    UserHypeTrackRepository userHypeTrackRepository;

    User user = User.create("test@gmail.com", "nickname");
    Genre rockGenre = Genre.create("락", "rock");

    @Test
    @DisplayName("하입트랙 exists / find 테스트")
    void hypedTrackExistsAndFindQueryTest() {
        Track rockTrack = Track.create("rock-1", "Rock Artist 1", "Rock Album 1", "Rock Track 1",
                "https://example.com/preview/rock1.mp3", "https://example.com/track/rock1", "https://example.com/art/rock1.jpg",
                Instant.now(), rockGenre, "US", "ITUNES");
        UserHypeTrack userHypeTrack = UserHypeTrack.create(user, rockTrack);

        entityManager.persistAndFlush(user);
        entityManager.persistAndFlush(rockGenre);
        entityManager.persistAndFlush(rockTrack);
        entityManager.persistAndFlush(userHypeTrack);

        User anotherUser = User.create("another@gmail.com", "another");
        entityManager.persistAndFlush(anotherUser);

        Track anotherRockTrack = Track.create("rock-2", "Rock Artist 2", "Rock Album 2", "Rock Track 2",
                "https://example.com/preview/rock2.mp3", "https://example.com/track/rock2", "https://example.com/art/rock2.jpg",
                Instant.now(), rockGenre, "US", "ITUNES");
        entityManager.persistAndFlush(anotherRockTrack);

        assertThat(userHypeTrackRepository.existsByUser_IdAndTrack_Id(user.getId(), rockTrack.getId())).isTrue();
        assertThat(userHypeTrackRepository.existsByUser_IdAndTrack_Id(user.getId(), anotherRockTrack.getId())).isFalse();
        assertThat(userHypeTrackRepository.existsByUser_IdAndTrack_Id(anotherUser.getId(), rockTrack.getId())).isFalse();
        assertThat(userHypeTrackRepository.existsByUser_IdAndTrack_Id(anotherUser.getId(), anotherRockTrack.getId())).isFalse();

        assertThat(userHypeTrackRepository.findByUser_IdAndTrack_Id(user.getId(), rockTrack.getId())).isNotEmpty();
        assertThat(userHypeTrackRepository.findByUser_IdAndTrack_Id(user.getId(), anotherRockTrack.getId())).isEmpty();
        assertThat(userHypeTrackRepository.findByUser_IdAndTrack_Id(anotherUser.getId(), rockTrack.getId())).isEmpty();
        assertThat(userHypeTrackRepository.findByUser_IdAndTrack_Id(anotherUser.getId(), anotherRockTrack.getId())).isEmpty();
    }

    @Test
    @DisplayName("내가 하입한 트랙 페이지네이션 테스트")
    void myHypedTrackQueryTest() {
        entityManager.persistAndFlush(user);
        entityManager.persistAndFlush(rockGenre);
        ArrayList<Track> testTrackList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Track testRockTrack = Track.create("rock-" + i, "Rock Artist " + i, "Rock Album " + i, "Rock Track " + i,
                    "https://example.com/preview/rock" + i + ".mp3", "https://example.com/track/rock" + i, "https://example.com/art/rock" + i + ".jpg",
                    Instant.now(), rockGenre, "US", "ITUNES");
            UserHypeTrack userhypeTrack = UserHypeTrack.create(user, testRockTrack);
            testTrackList.add(testRockTrack);

            entityManager.persistAndFlush(testRockTrack);
            entityManager.persistAndFlush(userhypeTrack);
        }

        List<UserHypeTrack> firstPage = userHypeTrackRepository.findUserHypeTracksByUserId(user.getId(), null, PageRequest.of(0, 10));
        assertThat(firstPage).extracting((uht) -> uht.getTrack().getId()).containsExactlyElementsOf(testTrackList.subList(testTrackList.size() - 10, testTrackList.size()).stream().map(Track::getId).toList().reversed());

        List<UserHypeTrack> secondPage = userHypeTrackRepository.findUserHypeTracksByUserId(user.getId(), firstPage.getLast().getId(), PageRequest.of(0, 10));
        assertThat(secondPage).extracting((uht) -> uht.getTrack().getId()).containsExactlyElementsOf(testTrackList.subList(0, testTrackList.size() - 10).stream().map(Track::getId).toList().reversed());

        List<UserHypeTrack> lastPage = userHypeTrackRepository.findUserHypeTracksByUserId(user.getId(), secondPage.getLast().getId(), PageRequest.of(0, 10));
        assertThat(lastPage).extracting((uht) -> uht.getTrack().getId()).isEmpty();
    }

    @Test
    @DisplayName("첫 다섯명 하입 유저 조회 기능 테스트")
    void firstFiveHypeUserTest() {
        entityManager.persistAndFlush(rockGenre);
        Track rockTrack = Track.create("rock-1", "Rock Artist 1", "Rock Album 1", "Rock Track 1",
                "https://example.com/preview/rock1.mp3", "https://example.com/track/rock1", "https://example.com/art/rock1.jpg",
                Instant.now(), rockGenre, "US", "ITUNES");
        entityManager.persistAndFlush(rockTrack);

        List<User> users = new ArrayList<>();

        for(int i=0; i<10; i++) {
            User user = User.create("test" + i + "@gmail.com", "test" + i);
            entityManager.persistAndFlush(user);

            users.add(user);

            UserHypeTrack userHypeTrack = UserHypeTrack.create(user, rockTrack);
            entityManager.persistAndFlush(userHypeTrack);
        }

        List<UserHypeTrack> userHypeTracks = userHypeTrackRepository.findFirstFiveHypeUsers(rockTrack.getId());
        assertThat(userHypeTracks).extracting(UserHypeTrack::getUser).extracting(User::getId)
                .containsExactlyElementsOf(users.subList(0,5).stream().map(User::getId).toList());

    }
}
