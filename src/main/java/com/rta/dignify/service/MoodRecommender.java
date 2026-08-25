package com.rta.dignify.service;

import com.rta.dignify.domain.Track;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /// 참고로 시드1은 14.0ms다. 재현은 아래 scanWindow 주석과 같은 방법.
    static final int SEEDS = 3;
    /// 리마스터·인스트·커버는 사실상 같은 녹음이다. 9만 곡 규모에서 최근접의 24.7%가 여기 걸렸다.
    /// 정렬로 쓰면 이것들이 통째로 앞자리를 먹으므로 슬롯 때보다 오히려 더 중요하다.
    static final double DUPLICATE_SIM = 0.95;

    private final JdbcTemplate jdbcTemplate;
    private final TrackRepository trackRepository;

    /// 이 페이지에 넣을 트랙 id를 무드 유사도 내림차순으로. **빈 리스트면 호출부는 종전
    /// 무작위 정렬로 간다** — 게스트, 하입이 없는 유저, 하입한 곡에 벡터가 없는 유저가 전부 여기로 빠진다.
    /// 벡터가 아직 없는 곡(활성 6만 4천 중 248곡)은 이 경로에 안 뜨고 무작위 폴백에서만 나온다.
    public List<Long> orderedTrackIds(Long userId, int limit, int offset) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        List<Seed> seeds = findSeeds(userId);
        if (seeds.isEmpty()) {
            return List.of();
        }
        // **모든 유저가 전 카탈로그를 본다.** 장르 선택 화면을 걷어낸 뒤로 `user_genres`는
        // 기존 유저만 값을 갖고 바꿀 방법이 없는 죽은 설정이라 2026-08-24부터 안 읽는다.
        // orderSql의 장르 필터 자리는 남겨 둔다 — 장르가 돌아오면 이 리스트만 채우면 되고,
        // 지우면 SQL 조립과 그 테스트를 통째로 다시 써야 한다.
        // 대가는 스캔이 8만 5천 행 전체로 늘어나는 것이다(README 실측 6.6ms → 29ms).
        List<Long> genreIds = List.of();
        List<Long> seedArtistIds = trackRepository.findAllById(
                        seeds.stream().map(Seed::trackId).toList()).stream()
                .map(Track::getArtistId).filter(Objects::nonNull).distinct().toList();

        List<Object> params = new ArrayList<>(List.of(flatten(seeds)));
        params.addAll(genreIds);                  // 선택 장르 (없으면 필터 자체가 빠진다)
        params.add(userId);                       // 하입 제외 조인
        params.addAll(seedArtistIds);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.queryForList(
                orderSql(seeds.size(), genreIds.size(), seedArtistIds.size(), scanWindow(limit, offset)),
                Long.class, params.toArray());
    }

    /// 이 페이지 아래에 아직 곡이 남아 있는지.
    ///
    /// **페이지가 짧은 것과 곡이 없는 것은 다르다.** 유사도 순위는 벡터 전체를 0.95부터 0까지
    /// 한 줄로 덮는다. 짧은 페이지는 순위가 끝나서가 아니라 스캔 창(K) 안이 전부 걸러졌기
    /// 때문이고(꺼진 곡·이미 하입·리마스터·같은 아티스트), 창 아래에는 곡이 그대로 있다.
    ///
    /// 스캔에는 조건이 없어서 결과가 항상 `min(K, 벡터 수)`다. 그러니 **K가 벡터 수보다 작으면
    /// 스캔은 꽉 찼고, 아래에 남아 있다**는 뜻이다. 추가 조회 없이 이 비교 하나로 갈린다.
    ///
    /// 이 판정을 페이지 길이로 하면(예전 방식) 창이 좁았을 뿐인데 커서를 끊어 피드가 그 자리에서
    /// 끝난다. 실측으로 상위 300개 중 9개만 살아남는 시드가 있었다.
    /// ponytail: 캐시 없이 매 요청 count다. 로컬 실측 3ms로 스캔(약 30ms)의 10분의 1이라
    /// 캐시가 버는 게 없고, 캐시를 두면 배치로 벡터가 늘었을 때 낡은 값이 남는다.
    public boolean hasMoreBelow(int limit, int offset) {
        Long vectorCount = jdbcTemplate.queryForObject("SELECT count(*) FROM track_vectors", Long.class);
        return vectorCount != null && scanWindow(limit, offset) < vectorCount;
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
        String cols = IntStream.range(0, DIMS).mapToObj(d -> "v" + d).collect(Collectors.joining(","));
        String sql = "SELECT track_id," + cols + " FROM track_vectors WHERE track_id IN ("
                + placeholders(trackIds.size()) + ")";
        Map<Long, Long> matches = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            float[] vector = new float[DIMS];
            for (int d = 0; d < DIMS; d++) {
                vector[d] = rs.getFloat("v" + d);
            }
            matches.put(rs.getLong("track_id"), bestSeed(vector, seeds));
        }, trackIds.toArray());
        return matches;
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

    /// 내적 스캔에서 몇 개를 받아올지. 비활성·하입·큐레이션 곡과 유사도 0.95 이상이 **뒤에서**
    /// 걸러지므로 필요한 수보다 넉넉히 받는다.
    ///
    /// **넉넉함이 성능이 아니라 정확성 문제다(2026-08-24).** 상위 K개가 다 걸러지면 페이지가
    /// 짧아지고, 짧은 페이지는 `FeedService`가 후보 소진으로 읽어 **커서를 끊는다.** 즉 피드가
    /// 그 자리에서 끝난다. 예전엔 general 폴백이 받아 줬지만 그 단계를 없앴다.
    ///
    /// 시드 20세트로 잰 상위 K개의 생존 수(로컬, 전부 활성):
    /// K=40이면 중앙값 37인데 **최소가 2**다 — 시드가 리마스터·커버 뭉치 안에 있으면 상위 40개가
    /// 통째로 0.95 컷에 걸린다. K=160이면 최소 122, K=300이면 최소 262다.
    /// 운영은 벡터의 34%만 활성이라 여기에 0.34를 곱해야 한다(되살리기 뒤엔 0.76).
    /// K=300이면 운영 최악에도 89곡이 남아 한 페이지 30곡의 세 배다.
    ///
    /// **K는 사실상 공짜다.** 스캔이 벡터 전체를 훑는 비용이 지배적이라 정렬 힙만 커진다.
    /// 로컬 실측(시드3, 30곡): K=300 23.4ms, K=840 23.5ms, K=1740 25.8ms.
    static int scanWindow(int limit, int offset) {
        return (limit + offset) * 6 + 120;
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
    /// 장르만 예외로 스캔 안에 있는데, `track_vectors.genre_id`가 tracks 복제본이라 조인이 아니다 —
    /// 곱셈 전에 행이 걸러져서 오히려 싸진다(로컬 실측 조인형 43.9ms vs 이 형태 6.6ms).
    /// 나머지 제외 조건은 상위 K개만 남은 바깥 쿼리에서 건다.
    ///
    /// 정렬 키에 track_id를 덧붙이는 건 동점 때문이다. 같은 유사도가 페이지마다 다른 순서로
    /// 나오면 OFFSET 페이징이 곡을 건너뛰거나 중복시킨다.
    static String orderSql(int seedCount, int genreCount, int seedArtistCount, int window) {
        // 시드별 내적을 s0..sN으로 뽑는 가장 안쪽 층. GREATEST를 **같은 층에서** 쓰면 별칭을 못 봐서
        // 내적 식을 두 번 적어야 하고, 그러면 8만 5천 행에 곱셈이 두 배로 붙는다. 그래서 한 겹 감싼다.
        String dotCols = IntStream.range(0, seedCount)
                .mapToObj(s -> "(" + IntStream.range(0, DIMS).mapToObj(d -> "v" + d + "*?").collect(Collectors.joining("+")) + ") AS s" + s)
                .collect(Collectors.joining(","));
        String simCols = IntStream.range(0, seedCount).mapToObj(s -> "s" + s).collect(Collectors.joining(","));
        String sim = seedCount == 1 ? "s0" : "GREATEST(" + simCols + ")";
        // 고른 장르가 없으면 필터를 통째로 뺀다 — 빈 IN 절은 후보를 0으로 만든다.
        String genreFilter = genreCount == 0 ? ""
                : "WHERE genre_id IN (" + placeholders(genreCount) + ") ";
        // 같은 아티스트는 정확하지만 이미 아는 걸 또 주는 것이다. 디깅 앱에선 추천이 아니다.
        String artistFilter = seedArtistCount == 0 ? ""
                : " AND (t.artist_id IS NULL OR t.artist_id NOT IN (" + placeholders(seedArtistCount) + "))";
        return "SELECT track_id FROM ("
                + "SELECT t.track_id, c.sim, ROW_NUMBER() OVER (PARTITION BY " + bestSeedCase(seedCount)
                + " ORDER BY c.sim DESC, t.track_id) AS rn FROM ("
                + "SELECT track_id," + simCols + "," + sim + " AS sim FROM ("
                + "SELECT track_id," + dotCols + " FROM track_vectors " + genreFilter + ") x "
                + "ORDER BY sim DESC, track_id LIMIT " + window + ") c "
                + "JOIN tracks t ON t.track_id = c.track_id "
                + "LEFT JOIN users_hype_tracks uht ON uht.track_id = t.track_id AND uht.user_id = ? "
                + "LEFT JOIN curation_tracks cur ON cur.track_id = t.track_id AND cur.is_active IS TRUE "
                + "WHERE t.is_active IS TRUE AND uht.user_hype_track_id IS NULL "
                + "AND cur.curation_track_id IS NULL AND c.sim < " + DUPLICATE_SIM + artistFilter
                + ") r ORDER BY rn, sim DESC, track_id LIMIT ? OFFSET ?";
    }

    /// 이 곡을 끌어올린 시드가 몇 번인지. `ROW_NUMBER`의 PARTITION 키다.
    ///
    /// **시드마다 몫을 주려고 있는 것이다.** 유사도 하나로만 줄 세우면 하입 곡 중 밀집된 지역에
    /// 있는 하나가 상위를 통째로 먹고 나머지 시드는 한 칸도 못 얻는다. 시드별로 번호를 매겨
    /// `ORDER BY rn`으로 내면 1등끼리, 2등끼리 묶여 나가서 세 시드가 고르게 섞인다.
    ///
    /// 슬롯 방식이던 시절의 "하입 3곡을 각각 독립 시드로 써서 1곡씩"이 원래 설계였는데,
    /// 정렬 방식으로 바꾸면서 이 규칙만 빠져 있었다(2026-08-24 복원).
    static String bestSeedCase(int seedCount) {
        if (seedCount == 1) {
            return "0";
        }
        StringBuilder when = new StringBuilder("CASE");
        for (int i = 0; i < seedCount - 1; i++) {
            int self = i;
            String cond = IntStream.range(i + 1, seedCount)
                    .mapToObj(j -> "c.s" + self + " >= c.s" + j)
                    .collect(Collectors.joining(" AND "));
            when.append(" WHEN ").append(cond).append(" THEN ").append(i);
        }
        return when.append(" ELSE ").append(seedCount - 1).append(" END").toString();
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
