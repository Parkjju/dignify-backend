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
    @DisplayName("실유저 픽 🔥는 앱 경로를 타서 마일스톤이 기록된다 - 안 그러면 첫 반응 푸시가 영영 안 간다")
    void reactOnRealUserPickRecordsMilestone() {
        userRepository.save(User.create("seed01@dignify.local", "old_seed1"));
        User real = userRepository.save(User.create("real@gmail.com", "real_user"));
        Pick pick = pickRepository.save(Pick.create(real, null, false));

        assertThat(seedPickService.react(pick.getId(), 3)).isEqualTo(1);
        // markNotified는 영속성 컨텍스트에 있다(커밋 때 flush) — JDBC로 읽으면 아직 0이다.
        assertThat(pickRepository.findById(pick.getId()).orElseThrow().getMaxNotifiedReactions()).isEqualTo(1);
    }

    @Test
    @DisplayName("하입 - 곡마다 per명, 다시 눌러도 이미 한 계정은 건너뛴다")
    void hypePickTracks() {
        userRepository.save(User.create("seed01@dignify.local", "old_seed1"));
        userRepository.save(User.create("seed02@dignify.local", "old_seed2"));
        User real = userRepository.save(User.create("real@gmail.com", "real_user"));
        List<Long> ids = tracks(2);
        Pick pick = pickRepository.save(Pick.create(real, null, false));
        ids.forEach(id -> jdbcTemplate.update(
                "INSERT INTO pick_tracks (pick_id, track_id, position, created_at, updated_at) VALUES (?, ?, 0 + ?, NOW(), NOW())",
                pick.getId(), id, ids.indexOf(id)));

        assertThat(seedPickService.hype(pick.getId(), 1)).isEqualTo(2);
        assertThat(seedPickService.hype(pick.getId(), 5)).isEqualTo(2);  // 계정 2개라 곡마다 1명 더
        assertThat(seedPickService.hype(pick.getId(), 5)).isZero();
    }

    @Test
    @DisplayName("계정만 만들기 - 실유저 기본 닉네임 형식, 가입일은 흩어지고, 100번째도 번호가 안 잘린다")
    void createAccounts() {
        userRepository.save(User.create("seed99@dignify.local", "old_seed99"));

        assertThat(seedPickService.createAccounts(20)).isEqualTo(20);
        assertThat(jdbcTemplate.queryForList("SELECT nickname FROM users WHERE email LIKE 'seed1__@dignify.local'", String.class))
                .hasSize(20).allMatch(n -> n.matches("digger_[0-9a-f]{8}"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = 'seed100@dignify.local'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT created_at::date) FROM users WHERE email LIKE 'seed1__@dignify.local'", Integer.class))
                .isGreaterThan(5);
    }

    @Test
    @DisplayName("재생 + - 앱에 보이는 값만 오르고 실제 재생 play_count는 그대로다")
    void addPlaysKeepsRealCount() {
        User real = userRepository.save(User.create("real@gmail.com", "real_user"));
        Pick pick = pickRepository.save(Pick.create(real, null, false));
        pick.play();
        pickRepository.flush();

        assertThat(seedPickService.addPlays(pick.getId(), 7)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT play_count FROM picks WHERE pick_id = ?", Integer.class, pick.getId())).isEqualTo(1);
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
