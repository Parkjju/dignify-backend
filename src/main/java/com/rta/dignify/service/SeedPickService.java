package com.rta.dignify.service;

import com.rta.dignify.dto.admin.SeedPickCreate;
import com.rta.dignify.dto.pick.PickReactionRequest;
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
import java.util.UUID;
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
    private final PickService pickService;

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
        // 비우면 실유저 기본 닉네임과 같은 형식으로 만든다(AuthService). 8자 16진이라 겹칠 일은 사실상 없지만 겹치면 다시 뽑는다.
        if (nickname.isEmpty()) {
            do {
                nickname = "digger_" + UUID.randomUUID().toString().substring(0, 8);
            } while (userRepository.existsByNickname(nickname));
        }
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
        // 'FM99900'은 두 자리를 보장하고 다섯 자리까지 늘어난다. lpad(.., 2)는 100부터 잘라 seed10과 겹친다.
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, nickname, is_onboarding_complete, created_at, updated_at)
                SELECT 'seed' || to_char(COALESCE(max(substring(email from 'seed(\\d+)@')::int), 0) + 1, 'FM99900') || '@dignify.local',
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
        return Map.of("pickId", pickId, "userId", userId, "nickname", nickname, "reactions", reactions);
    }

    /// 픽에 운영 계정 🔥를 want개까지 더 붙인다. 소유자·이미 누른 계정은 빠지고, 모자라면 있는 만큼만 들어간다.
    /// 반환값이 실제로 붙은 수다. 실유저 픽에도 된다.
    ///
    /// **SQL로 넣지 않고 앱과 같은 `PickService.setReaction`을 탄다.** SQL 반응은 max_notified_reactions를
    /// 안 올려서, 실유저 픽에 1개를 심으면 진짜 유저가 눌렀을 때 count가 2(마일스톤 아님)가 돼
    /// "첫 반응" 푸시가 영영 안 간다. 이 경로면 1·5·10번째에서 소유자에게 푸시가 **바로** 나간다
    /// (반응 푸시엔 시간대 필터가 없다). 운영 계정 픽은 소유자 기기가 없어 no-op이다.
    @Transactional
    public int react(Long pickId, int want) {
        if (want <= 0) return 0;
        List<Long> reactors = jdbcTemplate.queryForList("""
                SELECT u.user_id FROM picks p
                JOIN users u ON u.email LIKE ? AND u.user_id <> p.user_id
                WHERE p.pick_id = ? AND p.is_deleted = FALSE
                  AND NOT EXISTS (SELECT 1 FROM pick_reactions x WHERE x.pick_id = p.pick_id AND x.user_id = u.user_id)
                ORDER BY random()   -- 매번 같은 계정만 누르면 그것도 티가 난다
                LIMIT ?
                """, Long.class, SEED_EMAIL, pickId, want);
        // 이모지는 🔥만. 클라는 PickReaction.primary 하나만 그린다.
        reactors.forEach(userId -> pickService.setReaction(userId, pickId, new PickReactionRequest("🔥")));
        return reactors.size();
    }

    /// 픽의 곡마다 운영 계정 per명이 하입한다(이미 한 계정은 건너뛴다). 반환값은 새로 들어간 하입 수.
    /// 곡 상세의 firstHypers에 이 닉네임들이 뜬다.
    ///
    /// ponytail: 하입은 곡의 전역 인기 점수라 ColdStartRecommender 풀(하입 × 5, 상위 120곡)을 직접 민다.
    /// 지금 하입 총량이 작아 곡당 2~3개만 붙여도 신규 유저 첫 피드에 그 곡이 올라온다. 상한은 화면에서 per로만 건다.
    @Transactional
    public int hype(Long pickId, int per) {
        if (per <= 0) return 0;
        return jdbcTemplate.update("""
                INSERT INTO users_hype_tracks (user_id, track_id, is_seed, created_at, updated_at)
                SELECT u.user_id, pt.track_id, FALSE, NOW(), NOW()
                FROM pick_tracks pt
                JOIN picks p ON p.pick_id = pt.pick_id AND p.is_deleted = FALSE
                CROSS JOIN LATERAL (
                    SELECT u.user_id FROM users u
                    WHERE u.email LIKE ? AND u.user_id <> p.user_id
                      AND NOT EXISTS (SELECT 1 FROM users_hype_tracks h WHERE h.user_id = u.user_id AND h.track_id = pt.track_id)
                    ORDER BY random()
                    LIMIT ?
                ) u
                WHERE pt.pick_id = ?
                ON CONFLICT (user_id, track_id) DO NOTHING
                """, SEED_EMAIL, per, pickId);
    }

    /// 픽 표시 재생 수에 n을 더한다. 실제 재생(play_count)은 안 건드린다. 반환값은 더한 뒤 앱에 보이는 값.
    @Transactional
    public int addPlays(Long pickId, int n) {
        if (n < 1 || n > 1000) {
            throw new BusinessException(ErrorCode.METHOD_ARGUMENT_NOT_VALID, "n은 1~1000");
        }
        List<Integer> shown = jdbcTemplate.queryForList("""
                UPDATE picks SET seed_play_count = seed_play_count + ?
                WHERE pick_id = ? AND is_deleted = FALSE
                RETURNING play_count + seed_play_count
                """, Integer.class, n, pickId);
        if (shown.isEmpty()) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }
        return shown.getFirst();
    }

    /// 픽 없이 운영 계정만 count개 만든다. 반환값은 실제로 만든 수.
    ///
    /// 닉네임은 실유저가 닉네임을 안 바꿨을 때와 같은 형식(`digger_` + 16진 8자, AuthService)이라 섞이면 구분이 안 된다.
    /// 가입일은 PostHog 첫 이벤트(2026-07-17)부터 지금 사이로 흩는다 — 한날 가입이 몰리면 그것도 티가 난다.
    /// 닉네임이 우연히 겹치면 그 줄만 건너뛴다(ON CONFLICT).
    @Transactional
    public int createAccounts(int count) {
        if (count < 1 || count > 200) {
            throw new BusinessException(ErrorCode.METHOD_ARGUMENT_NOT_VALID, "count는 1~200");
        }
        return jdbcTemplate.update("""
                INSERT INTO users (email, nickname, is_onboarding_complete, created_at, updated_at)
                SELECT 'seed' || to_char(b.base + g, 'FM99900') || '@dignify.local',
                       'digger_' || substr(md5(random()::text), 1, 8),
                       TRUE, x.at, x.at
                FROM generate_series(1, ?) g
                CROSS JOIN (SELECT COALESCE(max(substring(email from 'seed(\\d+)@')::int), 0) AS base
                            FROM users WHERE email LIKE 'seed%@dignify.local') b
                CROSS JOIN LATERAL (SELECT TIMESTAMPTZ '2026-07-17' + random() * (NOW() - TIMESTAMPTZ '2026-07-17') AS at
                                    WHERE g IS NOT NULL) x   -- g를 걸어야 줄마다 random()이 다시 돈다
                ON CONFLICT DO NOTHING
                """, count);
    }

    /// 현황 한 판(`ops/picks-seed-status.sql`). 실유저 픽까지 최신 40개.
    /// available = 아직 안 누른 운영 계정 수(더 붙일 수 있는 최대치), seedHypes = 픽 곡들에 붙은 운영 계정 하입 수.
    /// mix는 지면 구성 — seed 비중이 과하면 실유저 픽이 첫 페이지에서 밀려난다.
    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        List<Map<String, Object>> picks = jdbcTemplate.queryForList("""
                SELECT p.pick_id AS "pickId", o.nickname AS owner, p.title, p.created_at::date::text AS created,
                       CASE WHEN o.email LIKE ? THEN 'seed' ELSE 'user' END AS kind,
                       p.play_count AS plays, p.seed_play_count AS "seedPlays",
                       (SELECT count(*) FROM pick_tracks t WHERE t.pick_id = p.pick_id)    AS tracks,
                       (SELECT count(*) FROM pick_reactions r WHERE r.pick_id = p.pick_id) AS reactions,
                       (SELECT count(*) FROM users u
                         WHERE u.email LIKE ? AND u.user_id <> p.user_id
                           AND NOT EXISTS (SELECT 1 FROM pick_reactions r
                                            WHERE r.pick_id = p.pick_id AND r.user_id = u.user_id)) AS available,
                       (SELECT count(*) FROM pick_tracks t JOIN users_hype_tracks h ON h.track_id = t.track_id
                          JOIN users u ON u.user_id = h.user_id AND u.email LIKE ?
                         WHERE t.pick_id = p.pick_id)                                       AS "seedHypes"
                FROM picks p JOIN users o ON o.user_id = p.user_id
                WHERE p.is_deleted = FALSE
                ORDER BY p.pick_id DESC
                LIMIT 40
                """, SEED_EMAIL, SEED_EMAIL, SEED_EMAIL);
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
