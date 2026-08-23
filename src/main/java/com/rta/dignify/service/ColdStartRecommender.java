package com.rta.dignify.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/// 하입이 없어 무드 정렬이 성립하지 않는 유저(게스트 · 신규)의 **첫 세 페이지**를 무작위 대신
/// "커뮤니티가 실제로 반응한 곡 중 서로 무드가 먼 것들"로 채운다. 첫 하입이 안 나오면 개인화가
/// 영원히 시작되지 않는데, 9만 곡에서 무작위 10곡이면 첫 화면이 낯선 이름만 가득할 확률이 높다.
///
/// 순서는 반응 수 내림차순이다 — 흩뿌리기는 어느 곡을 담을지만 정한다(§orderedTrackIds).
/// 창을 벗어나거나 풀이 얇으면 **빈 리스트를 낸다** — 호출부가 종전 무작위로 간다.
/// 유저 수가 적을수록 집계가 얇아 자동으로 무작위에 가까워지므로 최악의 경우가 지금 동작이다.
///
/// 인기순으로 자르기만 하면 안 된다. 2026-08-22 운영 데이터 실측으로 상위 30곡의 평균 쌍유사도가
/// 0.33(최대 0.87)인데 무작위 30곡은 -0.01이다. 즉 인기 상위는 한 덩어리로 뭉쳐 있어서
/// 그대로 쓰면 첫 화면이 한 가지 색이 된다. 그래서 뽑은 뒤 서로 멀게 흩뿌린다(§spread).
@RequiredArgsConstructor
@Component
public class ColdStartRecommender {
    /// 콜드스타트가 담당하는 범위. 10곡 × 3페이지다. 그 뒤로는 무작위로 넘긴다 —
    /// 세 페이지를 보고도 하입이 하나도 없으면 인기 풀이 이 유저에게 안 맞는다는 뜻이다.
    static final int WINDOW = 30;
    /// 인기순 상위 몇 곡을 후보 원본으로 볼지. 운영 실측으로 120등의 점수가 7점(하입 1 + 청취 2)이라
    /// 여기까지는 "반응이 있었다"고 부를 수 있다. 더 늘리면 무작위와 구분이 안 되기 시작한다.
    static final int POOL = 120;
    /// 셔플로 추려 흩뿌리기에 넣을 수. 120 → 셔플 60 → 흩뿌려 30이 실측상 가장 좋았다
    /// (평균 쌍유사도 0.09, 유저 간 곡 겹침 35%). 셔플 없이 상위 60만 쓰면 0.19에 겹침 78%다.
    static final int CANDIDATES = 60;
    /// 하입 1건을 청취 몇 건으로 칠지. 단순 합은 쓸 수 없다 — 전체가 하입 1,344 대 청취 3,591이라
    /// 합으로 놓으면 청취가 순위를 지배하는데, 청취는 유저의 선택이 아니라 **피드가 보여준 횟수**에 가깝다.
    static final int HYPE_WEIGHT = 5;

    private final JdbcTemplate jdbcTemplate;

    /// 이 페이지에 넣을 트랙 id. 빈 리스트면 호출부는 종전 무작위 정렬로 간다.
    /// seed는 커서에 실려 페이지 간 유지되므로 같은 세션에서 같은 순서가 다시 나온다.
    public List<Long> orderedTrackIds(Long userId, int limit, int offset, int seed) {
        // 창을 반만 걸치는 페이지는 아예 안 받는다. 짧은 페이지를 돌려주면 FeedService가
        // '장르 풀 소진'으로 읽어 커서를 GENERAL로 넘겨버린다(getFeedList의 size == FETCH_LIMIT 분기).
        if (limit <= 0 || offset < 0 || offset + limit > WINDOW) {
            return List.of();
        }
        List<Candidate> pool = findPool(userId);
        // 창 전체를 못 채울 만큼 얇으면 통째로 포기한다. 위와 같은 이유로 페이지가 짧아지면 안 된다.
        if (pool.size() < WINDOW) {
            return List.of();
        }
        // 셔플 전에 인기 순위를 기억한다. 아래에서 순서를 되돌리는 데 쓴다.
        Map<Long, Integer> rank = IntStream.range(0, pool.size()).boxed()
                .collect(Collectors.toMap(i -> pool.get(i).trackId(), i -> i));
        Collections.shuffle(pool, new Random(seed));
        List<Long> picked = spread(pool.subList(0, Math.min(CANDIDATES, pool.size())), WINDOW);
        // **어느 30곡을 고를지는 그리디가, 어떤 순서로 낼지는 반응 수가 정한다.**
        // 그리디는 무드 공간의 바깥쪽부터 집으므로 그 순서를 그대로 내면 라이브·리믹스 같은
        // 변종 녹음이 첫 장을 먹고, 가장 많이 반응한 곡은 3페이지로 밀린다(2026-08-22 실측:
        // 첫 페이지가 랭킹 15~112위였다). 흩어짐은 집합의 성질이라 다시 정렬해도 그대로다.
        List<Long> ordered = picked.stream().sorted(Comparator.comparingInt(rank::get)).toList();
        return ordered.subList(offset, offset + limit);
    }

    /// 인기 상위 POOL곡을 벡터까지 한 번에. 제외 조건이 곧 이 기능의 품질이다.
    ///
    /// - **활성 큐레이션 곡 제외**: 전 유저에게 결정적으로 노출되므로 카운트가 인기가 아니라 편성이고,
    ///   유저는 그 곡들을 `/feed/curation` 세트에서 이미 본다.
    /// - **이 유저가 하입·청취한 곡 제외**: 재방문 때 같은 곡을 다시 주지 않는다. 덤으로 집계
    ///   자기오염도 같이 막힌다 — 자기 청취가 순위를 밀어 올려 2페이지에 1페이지 곡이 다시 뜨는 일이 없다.
    /// - **장르**: 고른 게 있으면 그 안에서만 뽑는다. 유저가 방금 선언한 취향 밖으로 첫 화면이
    ///   나가면 안 된다. 얇은 장르(Latin 6곡 등)는 위 WINDOW 검사에 걸려 무작위로 떨어진다.
    ///
    /// ponytail: 집계 캐시 없이 매 요청 GROUP BY다. 대상이 하입 1.3천 + 청취 3.6천 행이라 지금은
    /// 이게 더 싸다. 청취 로그가 수십만 행이 되면 집계 테이블이나 캐시로 옮길 것.
    private List<Candidate> findPool(Long userId) {
        List<Long> genreIds = userId == null ? List.<Long>of()
                : jdbcTemplate.queryForList("SELECT genre_id FROM user_genres WHERE user_id = ?", Long.class, userId);
        // 파라미터 순서는 SQL에 나오는 순서 그대로 — 하입 조인, 청취 조인, 그 다음 장르다.
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(userId);
        params.addAll(genreIds);
        return jdbcTemplate.query(poolSql(genreIds.size()), (rs, i) -> {
            float[] vector = new float[MoodRecommender.DIMS];
            for (int d = 0; d < MoodRecommender.DIMS; d++) {
                vector[d] = rs.getFloat("v" + d);
            }
            return new Candidate(rs.getLong("track_id"), vector);
        }, params.toArray());
    }

    /// 게스트(userId = null)도 그대로 탄다 — 조인 조건이 아무 행에도 안 걸려 제외가 0이 된다.
    /// `CAST(? AS BIGINT)`는 그 null 때문이다. 타입 없는 null 파라미터는 서버가 형을 못 정한다.
    static String poolSql(int genreCount) {
        String cols = IntStream.range(0, MoodRecommender.DIMS).mapToObj(d -> "v.v" + d).collect(Collectors.joining(","));
        // 빈 IN 절은 문법 오류다. 고른 장르가 없으면 필터를 통째로 뺀다.
        String genreFilter = genreCount == 0 ? ""
                : " AND v.genre_id IN (" + IntStream.range(0, genreCount).mapToObj(i -> "?").collect(Collectors.joining(",")) + ")";
        return "SELECT v.track_id," + cols + " FROM track_vectors v "
                + "JOIN (SELECT track_id, SUM(w) AS score FROM ("
                + "SELECT track_id, " + HYPE_WEIGHT + " AS w FROM users_hype_tracks "
                + "UNION ALL SELECT track_id, 1 FROM listened_tracks) e GROUP BY track_id) p "
                + "ON p.track_id = v.track_id "
                + "JOIN tracks t ON t.track_id = v.track_id "
                + "LEFT JOIN curation_tracks cur ON cur.track_id = v.track_id AND cur.is_active IS TRUE "
                + "LEFT JOIN users_hype_tracks uht ON uht.track_id = v.track_id AND uht.user_id = CAST(? AS BIGINT) "
                + "LEFT JOIN listened_tracks lt ON lt.track_id = v.track_id AND lt.user_id = CAST(? AS BIGINT) "
                + "WHERE t.is_active IS TRUE AND cur.curation_track_id IS NULL "
                + "AND uht.user_hype_track_id IS NULL AND lt.listened_track_id IS NULL" + genreFilter
                // 동점이 페이지마다 다른 순서로 나오면 셔플 결과가 흔들린다. track_id로 갈라 고정한다.
                + " ORDER BY p.score DESC, v.track_id LIMIT " + POOL;
    }

    /// 앞에서부터 하나 잡고, 이미 고른 것들과의 **최대 유사도가 가장 작은** 곡을 반복해서 붙인다.
    /// 평균이 아니라 최대인 이유는, 이미 고른 것 하나와만 닮아도 유저 눈에는 중복이기 때문이다.
    ///
    /// ponytail: 매번 남은 후보를 전부 훑는 O(뽑을수 × 후보수 × 뽑을수 × 차원)이다.
    /// 30 × 60 × 30 × 32면 1ms 안쪽이라 그대로 둔다. 후보를 수백으로 늘릴 거면 최대 유사도를
    /// 누적 배열로 들고 다니는 형태로 바꿀 것.
    static List<Long> spread(List<Candidate> candidates, int count) {
        List<Candidate> remaining = new ArrayList<>(candidates);
        List<Candidate> picked = new ArrayList<>();
        picked.add(remaining.remove(0));
        while (picked.size() < count && !remaining.isEmpty()) {
            int best = 0;
            double bestSimilarity = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                double similarity = maxSimilarity(remaining.get(i), picked);
                if (similarity < bestSimilarity) {
                    bestSimilarity = similarity;
                    best = i;
                }
            }
            picked.add(remaining.remove(best));
        }
        return picked.stream().map(Candidate::trackId).toList();
    }

    private static double maxSimilarity(Candidate candidate, List<Candidate> picked) {
        double max = -Double.MAX_VALUE;
        for (Candidate other : picked) {
            max = Math.max(max, candidate.similarity(other));
        }
        return max;
    }

    /// 벡터는 L2 정규화돼 있어 내적이 곧 코사인이다(load-vectors.sh).
    record Candidate(Long trackId, float[] vector) {
        double similarity(Candidate other) {
            double dot = 0;
            for (int d = 0; d < vector.length; d++) {
                dot += vector[d] * other.vector[d];
            }
            return dot;
        }
    }
}
