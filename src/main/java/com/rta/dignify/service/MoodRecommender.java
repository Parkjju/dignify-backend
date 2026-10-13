package com.rta.dignify.service;

import com.rta.dignify.domain.FeedState;
import com.rta.dignify.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/// 유저가 고른 장르 **안에서** 최근 하입한 곡과 무드가 가까운 순으로 트랙을 정렬한다.
/// 장르를 하나도 안 고른 유저는 전 카탈로그가 후보다 — 그 유저는 지금도 GENERAL 폴백으로 전체를 본다.
///
/// 피드에 무드 칸을 따로 끼우지 않는다. 풀스크린 스와이프 피드에서 "10칸 중 3칸이 개인화"는
/// 유저에게 존재하지 않는 정보라, 틀려도 안 보이고 n이 작아 측정도 안 된다.
/// 순서만 바꾸면 최악의 경우가 지금 동작(장르 내 무작위)이라 손해가 0이다.
///
/// **하입 곡들의 평균을 내지 않는다.** 최근 몇 곡을 각각 독립 seed로 두고 seed별 유사도의
/// 최대값으로 정렬한다. 평균 벡터는 무드 평면 정중앙, 즉 아무 색도 없는 곡을 가리킨다.
///
/// `track_vectors`는 JPA가 모르는 테이블이다 — 32컬럼짜리 엔티티를 매핑만 위해 두는 건 낭비라
/// `mood-pilot/load-vectors.sh`가 직접 채운다. 벡터는 L2 정규화돼 있어 내적이 곧 코사인이다.
@RequiredArgsConstructor
@Component
public class MoodRecommender {
    /// load-vectors.sh의 DIMS와 반드시 같아야 한다. 32인 이유는 품질이 아니라 지연이다 —
    /// 64는 품질이 같은데 seed 3개 스캔이 226ms라 /feed p95(209ms)를 두 배로 만든다.
    static final int DIMS = 32;
    /// 스캔 비용은 SEEDS × DIMS에 선형이다(행마다 GREATEST(내적...)을 계산한다).
    ///
    /// **5에서 3으로 되돌렸다(2026-08-24).** 5로 올렸던 목적은 잘못 누른 하입 하나의 지분을
    /// 1/3에서 1/5로 줄이는 것이었는데, 시드 고정(`users_hype_tracks.is_seed`)이 그 문제를
    /// 희석이 아니라 지목으로 푼다. 장르 필터가 빠지면서 스캔이 벡터 전체로 늘어난 것도 이유다.
    ///
    /// 로컬 실측(벡터 85,463행, 장르 필터 없음, 10곡/K=40): 시드5 43.4ms → **시드3 24.1ms**.
    /// 참고로 시드1은 14.0ms다.
    static final int SEEDS = 3;
    /// 리마스터·인스트·커버는 사실상 같은 녹음이다. 9만 곡 규모에서 최근접의 24.7%가 여기 걸렸다.
    /// 정렬로 쓰면 이것들이 통째로 앞자리를 먹으므로 슬롯 때보다 오히려 더 중요하다.
    static final double DUPLICATE_SIM = 0.95;
    /// θ 경계 여유. 경계 곡이 부동소수 오차로 한 번 더 나오는 걸 시뮬레이션에서 봤다(KAN-23).
    static final double EPSILON = 1e-6;

    private final JdbcTemplate jdbcTemplate;

    /// 이 페이지에 넣을 트랙 id를 무드 유사도 내림차순으로. **빈 리스트면 호출부는 종전
    /// 무작위 정렬로 간다** — 게스트, 하입이 없는 유저, 하입한 곡에 벡터가 없는 유저가 전부 여기로 빠진다.
    /// 벡터가 아직 없는 곡(활성 6만 4천 중 248곡)은 이 경로에 안 뜨고 무작위 폴백에서만 나온다.
    ///
    /// **유저의 `feed_state`를 읽고 갱신한다**(호출부 트랜잭션이 저장). 같은 유저가 다시 부르면
    /// 다음 페이지가 나온다 — 위치가 커서가 아니라 유저에 있기 때문이다(KAN-23).
    /// `limit`을 못 채우면 끝까지 본 것이라 상태를 지우고, 다음 요청은 처음부터다.
    public List<Long> orderedTrackIds(User user, int limit) {
        if (user == null || limit <= 0) {
            return List.of();
        }
        List<Seed> seeds = findSeeds(user.getId());
        if (seeds.isEmpty()) {
            return List.of();
        }
        FeedState state = FeedState.begin(user.getFeedState(), seeds.stream().map(Seed::trackId).toList());
        // 같은 시드가 두 번 빠졌으면 θ가 낮은(= 더 많이 거르는) 쪽 하나로 합친다. 벡터가 지워진 시드는 버린다.
        Map<Long, Double> droppedTheta = state.dropped().stream()
                .collect(Collectors.toMap(FeedState.Dropped::seedId, FeedState.Dropped::theta, Math::min));
        List<Seed> dropped = new ArrayList<>();
        vectors(List.copyOf(droppedTheta.keySet())).forEach((id, vector) -> dropped.add(new Seed(id, vector)));

        // **모든 유저가 전 카탈로그를 본다.** 장르 선택 화면을 걷어낸 뒤로 `user_genres`는
        // 기존 유저만 값을 갖고 바꿀 방법이 없는 죽은 설정이라 2026-08-24부터 안 읽는다.
        // orderSql의 장르 필터 자리는 남겨 둔다 — 장르가 돌아오면 이 리스트만 채우면 되고,
        // 지우면 SQL 조립과 그 테스트를 통째로 다시 써야 한다.
        List<Long> genreIds = List.of();

        List<Object> params = new ArrayList<>(List.of(flatten(seeds)));
        params.addAll(List.of(flatten(dropped)));
        params.addAll(genreIds);
        params.add(state.theta() - EPSILON);
        dropped.forEach(d -> params.add(droppedTheta.get(d.trackId()) - EPSILON));
        params.add(limit);
        params.add(user.getId());                 // 창 크기(하입 수)
        params.add(user.getId());                 // 하입 제외 조인
        params.add(limit);
        List<Long> ids = new ArrayList<>();
        List<Double> sims = new ArrayList<>();
        jdbcTemplate.query(orderSql(seeds.size(), dropped.size(), genreIds.size()), rs -> {
            ids.add(rs.getLong("track_id"));
            sims.add(rs.getDouble("sim"));
        }, params.toArray());

        user.updateFeedState(ids.size() < limit ? null : state.sentDownTo(sims.get(sims.size() - 1)));
        return ids;
    }

    /// 이 곡들이 **어느 하입 곡 때문에 떴는지**. 정렬은 시드별 내적의 최댓값으로 하므로,
    /// 그 최댓값을 만든 시드가 곧 이유다. 스캔 SQL을 건드리지 않고 상위 K개(한 페이지 10곡)만
    /// 벡터를 다시 받아 자바에서 고른다 — 10 × 5 × 32번 곱셈이라 무시할 수 있고,
    /// 스캔 쿼리에 컬럼을 더하는 쪽은 정렬 대상 전체(9만 행)를 건드린다.
    ///
    /// 시드가 없거나(게스트·하입 0) 후보에 벡터가 없으면 빈 맵이다. 호출부는 근거 없이 그냥 낸다.
    public Map<Long, Long> seedMatches(Long userId, List<Long> trackIds) {
        if (userId == null || trackIds.isEmpty()) {
            return Map.of();
        }
        List<Seed> seeds = findSeeds(userId);
        if (seeds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> matches = new HashMap<>();
        vectors(trackIds).forEach((id, vector) -> matches.put(id, bestSeed(vector, seeds)));
        return matches;
    }

    /// 이 곡들의 벡터. 벡터가 없는 곡은 맵에 없다.
    private Map<Long, float[]> vectors(List<Long> trackIds) {
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        String cols = IntStream.range(0, DIMS).mapToObj(d -> "v" + d).collect(Collectors.joining(","));
        String sql = "SELECT track_id," + cols + " FROM track_vectors WHERE track_id IN ("
                + placeholders(trackIds.size()) + ")";
        Map<Long, float[]> vectors = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            float[] vector = new float[DIMS];
            for (int d = 0; d < DIMS; d++) {
                vector[d] = rs.getFloat("v" + d);
            }
            vectors.put(rs.getLong("track_id"), vector);
        }, trackIds.toArray());
        return vectors;
    }

    /// 내적이 가장 큰 시드. 벡터가 L2 정규화돼 있어 내적이 곧 코사인이다.
    static Long bestSeed(float[] vector, List<Seed> seeds) {
        Seed best = seeds.get(0);
        double bestDot = -Double.MAX_VALUE;
        for (Seed seed : seeds) {
            double dot = 0;
            for (int d = 0; d < vector.length; d++) {
                dot += vector[d] * seed.vector()[d];
            }
            if (dot > bestDot) {
                bestDot = dot;
                best = seed;
            }
        }
        return best.trackId();
    }

    /// 유저가 하입한 곡 중 벡터가 있는 것 SEEDS개를 벡터까지 한 번에 가져온다.
    /// (user_id, track_id) 유니크 제약이 만든 인덱스를 타므로 스캔이 아니다.
    ///
    /// **직접 고정한 곡이 하나라도 있으면 그것만 쓴다.** 고정과 최근 하입을 섞으면 유저가
    /// 고른 곡의 지분이 다음 하입 한 번에 밀려서, 고정했다는 사실이 화면에서 사라진다.
    /// 아무것도 고정하지 않은 유저는 종전대로 최근 SEEDS곡이다 — 기본 동작이 그대로 남는다.
    ///
    /// 정렬에 `is_seed DESC`를 먼저 두는 건 한 번만 조회하기 위해서다. 고정한 곡이 SEEDS개를
    /// 넘으면 그중 최근 것부터 잘린다(앱도 같은 수로 선택을 막는다).
    private List<Seed> findSeeds(Long userId) {
        String cols = IntStream.range(0, DIMS).mapToObj(d -> "v.v" + d).collect(Collectors.joining(","));
        String sql = "SELECT v.track_id, uht.is_seed," + cols + " FROM track_vectors v "
                + "JOIN users_hype_tracks uht ON uht.track_id = v.track_id "
                + "WHERE uht.user_id = ? ORDER BY uht.is_seed DESC, uht.user_hype_track_id DESC LIMIT " + SEEDS;
        List<Seed> pinned = new ArrayList<>();
        List<Seed> recent = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            float[] vector = new float[DIMS];
            for (int d = 0; d < DIMS; d++) {
                vector[d] = rs.getFloat("v" + d);
            }
            (rs.getBoolean("is_seed") ? pinned : recent).add(new Seed(rs.getLong("track_id"), vector));
        }, userId);
        return pinned.isEmpty() ? recent : pinned;
    }

    /// **조인을 내적 스캔 안에 넣지 말 것.** 운영 실측으로 tracks 조인 +67ms, 하입 조인 +203ms다.
    /// 장르·활성은 `track_vectors`의 복제 컬럼이라 조인이 아니고, 곱셈 전에 행을 거른다.
    ///
    /// **한 줄 정렬이다(2026-10-12, KAN-23).** 예전엔 상위 K개를 잘라 담당 시드별로 번갈아
    /// 꺼냈는데, K가 페이지마다 커지면서 범위 밖이던 곡이 앞 순번에 끼어들어 중복·누락을 냈다.
    /// 한 시드가 앞을 독차지하는 건 감수하기로 했다.
    ///
    /// 하입·큐레이션 곡은 점수순으로 `limit + 하입 수 + 큐레이션 수`개를 뽑은 뒤 조인으로 뺀다.
    /// 빠질 수 있는 최대치만큼 더 뽑으므로 곡이 남아 있는 한 항상 limit개가 찬다(로컬 실측 40ms).
    ///
    /// 정렬 키에 track_id를 덧붙이는 건 동점 때문이다. θ 경계가 동점 사이에 걸리면 곡이 빠질 수
    /// 있지만 실수 내적이 정확히 같을 일은 리마스터뿐이고, 그건 0.95 컷에 이미 걸린다.
    ///
    /// 파라미터: 시드 내적 → 빠진 시드 내적 → 장르 → θ → 빠진 시드별 θ → limit → userId ×2 → limit.
    static String orderSql(int seedCount, int droppedCount, int genreCount) {
        // 시드별 내적을 s0..sN, 빠진 시드는 d0..dN으로 뽑는 가장 안쪽 층. GREATEST를 같은 층에서
        // 쓰면 별칭을 못 봐서 내적 식을 두 번 적어야 하므로 한 겹 감싼다.
        //
        // **`OFFSET 0`을 지우지 말 것.** 없으면 플래너가 이 층을 펼쳐서 내적 식을 WHERE와 SELECT에
        // 그대로 복사해 행마다 두세 번 곱한다(EXPLAIN VERBOSE로 확인). 운영 복사본 실측(하입 245곡,
        // 빠진 시드 0개): 펼쳐진 형태 60ms → OFFSET 0 27ms. 빠진 시드 하나당 약 6ms, 5개면 56ms.
        String dotCols = dots("s", seedCount) + (droppedCount == 0 ? "" : "," + dots("d", droppedCount));
        String simCols = IntStream.range(0, seedCount).mapToObj(s -> "s" + s).collect(Collectors.joining(","));
        String sim = seedCount == 1 ? "s0" : "GREATEST(" + simCols + ")";
        // 고른 장르가 없으면 필터를 통째로 뺀다 — 빈 IN 절은 후보를 0으로 만든다.
        String genreFilter = genreCount == 0 ? ""
                : " AND genre_id IN (" + placeholders(genreCount) + ")";
        // 빠진 시드 덕분에 이미 나간 곡(그 시드와 유사도가 빠질 때의 θ 이상)은 다시 안 낸다.
        String droppedFilter = IntStream.range(0, droppedCount).mapToObj(d -> " AND d" + d + " < ?")
                .collect(Collectors.joining());
        return "SELECT c.track_id, c.sim FROM ("
                + "SELECT track_id," + sim + " AS sim FROM ("
                + "SELECT track_id," + dotCols + " FROM track_vectors WHERE is_active IS TRUE" + genreFilter + " OFFSET 0) x "
                + "WHERE " + sim + " < " + DUPLICATE_SIM + " AND " + sim + " < ?" + droppedFilter
                + " ORDER BY sim DESC, track_id LIMIT ? + (SELECT count(*) FROM users_hype_tracks WHERE user_id = ?)"
                + " + (SELECT count(*) FROM curation_tracks WHERE is_active IS TRUE)) c "
                + "LEFT JOIN users_hype_tracks uht ON uht.track_id = c.track_id AND uht.user_id = ? "
                + "LEFT JOIN curation_tracks cur ON cur.track_id = c.track_id AND cur.is_active IS TRUE "
                + "WHERE uht.user_hype_track_id IS NULL AND cur.curation_track_id IS NULL "
                + "ORDER BY c.sim DESC, c.track_id LIMIT ?";
    }

    private static String dots(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(s -> "(" + IntStream.range(0, DIMS).mapToObj(d -> "v" + d + "*?").collect(Collectors.joining("+")) + ") AS " + prefix + s)
                .collect(Collectors.joining(","));
    }

    private static String placeholders(int count) {
        return IntStream.range(0, count).mapToObj(i -> "?").collect(Collectors.joining(","));
    }

    /// 파라미터는 seed 순서 → 차원 순서로 평평하게 깐다. orderSql이 내적을 그 순서로 쓰기 때문에
    /// 한 칸만 밀려도 조용히 엉뚱한 유사도가 나온다(에러가 아니라 그냥 이상한 정렬이 된다).
    static Object[] flatten(List<Seed> seeds) {
        Object[] params = new Object[seeds.size() * DIMS];
        for (int s = 0; s < seeds.size(); s++) {
            for (int d = 0; d < DIMS; d++) {
                params[s * DIMS + d] = seeds.get(s).vector()[d];
            }
        }
        return params;
    }

    record Seed(Long trackId, float[] vector) {
    }
}
