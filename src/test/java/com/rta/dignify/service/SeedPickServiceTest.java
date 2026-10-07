package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Pick;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.admin.SeedPickCreate;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.PickRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class SeedPickServiceTest {
    @Autowired SeedPickService seedPickService;
    @Autowired GenreRepository genreRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired UserRepository userRepository;
    @Autowired PickRepository pickRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    List<Long> tracks(int n) {
        Genre genre = genreRepository.save(Genre.create("Rock", "락"));
        return IntStream.range(0, n).mapToObj(i -> trackRepository.save(Track.create("seed-" + i, "Artist " + i, "Album", "Track " + i,
                "https://example.com/p.mp3", "https://example.com/t", "https://example.com/a.jpg",
                Instant.now(), genre, "US", "ITUNES")).getId()).toList();
    }

    @Test
    @DisplayName("게시 - 곡 순서대로 들어가고, 하입은 소유자만, 🔥는 기존 시드 계정 수까지만 붙는다")
    void createSeedPick() {
        userRepository.save(User.create("seed01@dignify.local", "old_seed1"));
        userRepository.save(User.create("seed02@dignify.local", "old_seed2"));
        List<Long> ids = tracks(3);
        List<Long> order = List.of(ids.get(2), ids.get(0), ids.get(1));

        Map<String, Object> r = seedPickService.create(new SeedPickCreate("new_seed", "  제목  ", order, 5));
        Long pickId = (Long) r.get("pickId");

        assertThat(r.get("reactions")).isEqualTo(2);  // 5를 원해도 기존 계정 2개뿐
        assertThat(jdbcTemplate.queryForList("SELECT track_id FROM pick_tracks WHERE pick_id = ? ORDER BY position", Long.class, pickId))
                .isEqualTo(order);
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM users WHERE nickname = 'new_seed'", String.class))
                .isEqualTo("seed03@dignify.local");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT user_id FROM users_hype_tracks WHERE track_id IN (?, ?, ?)", Long.class,
                ids.get(0), ids.get(1), ids.get(2))).containsExactly((Long) r.get("userId"));
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM picks WHERE pick_id = ?", String.class, pickId)).isEqualTo("제목");
        assertThat(seedPickService.react(pickId, 1)).isZero();  // 계정 소진
    }

    @Test
    @DisplayName("실유저 픽에는 🔥를 못 넣는다 - 첫 반응 푸시가 막히기 때문")
    void reactRejectsRealUserPick() {
        userRepository.save(User.create("seed01@dignify.local", "old_seed1"));
        User real = userRepository.save(User.create("real@gmail.com", "real_user"));
        Pick pick = pickRepository.save(Pick.create(real, null, false));

        assertThatThrownBy(() -> seedPickService.react(pick.getId(), 1))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PICK_NOT_SEED_OWNED);
    }

    @Test
    @DisplayName("같은 곡 두 번 / 중복 닉네임은 아무것도 안 넣고 막힌다")
    void rejectsBadInput() {
        userRepository.save(User.create("seed01@dignify.local", "old_seed1"));
        Long id = tracks(1).get(0);

        assertThatThrownBy(() -> seedPickService.create(new SeedPickCreate("x", null, List.of(id, id), 0)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRACK_NOT_FOUND);
        assertThatThrownBy(() -> seedPickService.create(new SeedPickCreate("old_seed1", null, List.of(id), 0)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NICKNAME_DUPLICATE);
    }
}
