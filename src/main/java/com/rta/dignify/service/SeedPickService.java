package com.rta.dignify.service;

import com.rta.dignify.dto.admin.SeedPickCreate;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.global.util.ProfanityFilter;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// 운영 계정으로 픽 지면을 채운다. `ops/picks-seed-daily.sql`·`picks-seed-react.sql`을 옮긴 것.
///
/// 시드 계정은 email 도메인 `@dignify.local`로 갈린다. API 응답에 email이 안 실리므로 앱에선 안 보인다.
/// JPA 대신 SQL로 넣는 이유는 created_at을 과거로 둬야 해서다 — `@CreatedDate`가 덮어쓴다.
@RequiredArgsConstructor
@Service
public class SeedPickService {
    static final String SEED_EMAIL = "%@dignify.local";
    private static final String NICKNAME_PATTERN = "^[a-zA-Z0-9_가-힣]{1,20}$";

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

    /// 새 계정 생성 → 픽 게시 → 소유자가 곡 전부 하입 → 기존 시드 계정이 🔥. 한 트랜잭션이다.
    ///
    /// - 매일 **새 계정**으로 올린다. 같은 닉네임이 매일 뜨면 지면이 두 사람의 대화처럼 보인다.
    /// - 픽 created_at은 NOW()다. 목록은 pick_id DESC인데 카드는 created_at을 상대시간으로 그려서
    ///   과거로 넣으면 맨 위가 "5일 전"이 된다. 가입일만 과거로 둔다.
    /// - is_official은 FALSE. 정렬이 `is_official ASC`라 TRUE면 목록 맨 뒤로 밀린다.
    /// - 하입은 **소유자 한 명만**. 하입은 곡의 전역 인기 점수라(ColdStartRecommender) 계정을 더 붙이면
    ///   시드 곡이 신규 유저 첫 피드를 점령한다.
    /// - is_seed는 FALSE. 시드 계정 피드는 아무도 안 본다.
    @Transactional
    public Map<String, Object> create(SeedPickCreate request) {
        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        if (!nickname.matches(NICKNAME_PATTERN) || ProfanityFilter.contains(nickname)) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_INVALID);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
        }
        List<Long> trackIds = request.trackIds();
        // 같은 곡 두 번이면 uq_pick_track에 걸려 통째로 터진다. 없는 id도 FK로 터지니 먼저 막는다.
        if (trackIds == null || trackIds.isEmpty() || new HashSet<>(trackIds).size() != trackIds.size()
                || trackRepository.findAllById(trackIds).size() != trackIds.size()) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }
        String title = PickService.validatedTitle(request.title());
        int joinedDaysAgo = 7 + (int) (Math.random() * 20);
        String ids = trackIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 이메일은 seedNN 최대값 + 1로 채번한다. 손으로 세면 언젠가 겹친다.
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, nickname, is_onboarding_complete, created_at, updated_at)
                SELECT 'seed' || lpad((COALESCE(max(substring(email from 'seed(\\d+)@')::int), 0) + 1)::text, 2, '0')
                           || '@dignify.local',
                       ?, TRUE, NOW() - make_interval(days => ?), NOW()
                FROM users WHERE email LIKE 'seed%@dignify.local'
                RETURNING user_id
                """, Long.class, nickname, joinedDaysAgo);

        Long pickId = jdbcTemplate.queryForObject("""
                INSERT INTO picks (user_id, title, is_official, is_deleted, max_notified_reactions, created_at, updated_at)
                VALUES (?, ?, FALSE, FALSE, 0, NOW(), NOW())
                RETURNING pick_id
                """, Long.class, userId, title);

        jdbcTemplate.update("""
                INSERT INTO pick_tracks (pick_id, track_id, position, created_at, updated_at)
                SELECT ?, t.id, t.ord - 1, NOW(), NOW()
                FROM unnest(string_to_array(?, ',')::bigint[]) WITH ORDINALITY AS t(id, ord)
                """, pickId, ids);

        // 곡 상세의 firstHypers(오래된 순 5명)가 이걸 그린다. 시각은 가입일과 지금 사이로 흩는다 —
        // 다섯 곡이 같은 초에 찍히면 그것도 티가 난다.
        jdbcTemplate.update("""
                INSERT INTO users_hype_tracks (user_id, track_id, is_seed, created_at, updated_at)
                SELECT ?, h.id, FALSE, h.at, h.at
                FROM (SELECT t.id, NOW() - random() * make_interval(days => ?) AS at
                      FROM unnest(string_to_array(?, ',')::bigint[]) AS t(id)) h
                """, userId, joinedDaysAgo, ids);

        int reactions = react(pickId, request.reactions());
        return Map.of("pickId", pickId, "userId", userId, "reactions", reactions);
    }

    /// 시드 픽에 🔥를 want개까지 더 붙인다. 소유자·이미 누른 계정은 빠지고, 모자라면 있는 만큼만 들어간다.
    /// 반환값이 실제로 붙은 수다.
    ///
    /// ⚠️ **실유저 픽은 0행이다(owner email 조인이 막는다).** SQL 반응은 앱을 안 거쳐
    /// max_notified_reactions가 0으로 남는다. 실유저 픽에 1개를 심으면 진짜 유저가 눌렀을 때 count가 2가 되는데
    /// 2는 마일스톤이 아니라서 "첫 반응" 푸시가 영영 안 간다.
    @Transactional
    public int react(Long pickId, int want) {
        Boolean seedOwned = jdbcTemplate.query("""
                SELECT o.email LIKE ? FROM picks p JOIN users o ON o.user_id = p.user_id
                WHERE p.pick_id = ? AND p.is_deleted = FALSE
                """, rs -> rs.next() ? rs.getBoolean(1) : null, SEED_EMAIL, pickId);
        if (seedOwned == null) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }
        if (!seedOwned) {
            throw new BusinessException(ErrorCode.PICK_NOT_SEED_OWNED);
        }
        if (want <= 0) return 0;
        // 이모지는 🔥만. 클라는 PickReaction.primary 하나만 그린다.
        return jdbcTemplate.update("""
                INSERT INTO pick_reactions (pick_id, user_id, emoji, created_at, updated_at)
                SELECT p.pick_id, u.user_id, '🔥', NOW(), NOW()
                FROM picks p
                JOIN users u ON u.email LIKE ? AND u.user_id <> p.user_id
                WHERE p.pick_id = ?
                  AND NOT EXISTS (SELECT 1 FROM pick_reactions x WHERE x.pick_id = p.pick_id AND x.user_id = u.user_id)
                ORDER BY random()   -- 매일 같은 계정만 누르면 그것도 티가 난다
                LIMIT ?
                """, SEED_EMAIL, pickId, want);
    }

    /// 현황 한 판(`ops/picks-seed-status.sql`). available = 아직 안 누른 시드 계정 수(더 붙일 수 있는 최대치).
    /// mix는 지면 구성 — seed 비중이 과하면 실유저 픽이 첫 페이지에서 밀려난다.
    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        List<Map<String, Object>> picks = jdbcTemplate.queryForList("""
                SELECT p.pick_id AS "pickId", o.nickname AS owner, p.title, p.created_at::date::text AS created,
                       p.play_count AS plays,
                       (SELECT count(*) FROM pick_tracks t WHERE t.pick_id = p.pick_id)    AS tracks,
                       (SELECT count(*) FROM pick_reactions r WHERE r.pick_id = p.pick_id) AS reactions,
                       (SELECT count(*) FROM users u
                         WHERE u.email LIKE ? AND u.user_id <> p.user_id
                           AND NOT EXISTS (SELECT 1 FROM pick_reactions r
                                            WHERE r.pick_id = p.pick_id AND r.user_id = u.user_id)) AS available
                FROM picks p JOIN users o ON o.user_id = p.user_id
                WHERE o.email LIKE ? AND p.is_deleted = FALSE
                ORDER BY p.pick_id DESC
                LIMIT 30
                """, SEED_EMAIL, SEED_EMAIL);
        List<Map<String, Object>> mix = jdbcTemplate.queryForList("""
                SELECT CASE WHEN o.email LIKE ? THEN 'seed' ELSE 'user' END AS kind,
                       count(*) AS picks, count(DISTINCT p.user_id) AS accounts
                FROM picks p JOIN users o ON o.user_id = p.user_id
                WHERE p.is_deleted = FALSE
                GROUP BY 1 ORDER BY 1
                """, SEED_EMAIL);
        Long accounts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email LIKE ?", Long.class, SEED_EMAIL);
        return Map.of("accounts", accounts, "picks", picks, "mix", mix);
    }
}
