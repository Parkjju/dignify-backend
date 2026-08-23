package com.rta.dignify.service;

import com.rta.dignify.domain.Track;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
    /// 3 → 5는 2026-08-22 실측으로 1.54배, 실제 테이블 기준 110ms → 약 170ms다.
    /// 위 64차원 기각선(226ms) 안쪽이라 감수한다 — 얻는 건 잘못 누른 하입 하나의 지분이
    /// 1/3에서 1/5로 주는 것이다. 더 늘릴 거면 `mood-pilot/bench-db.sh`로 재측정이 먼저다.
    static final int SEEDS = 5;
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
        // 장르를 하나도 안 고른 유저는 전 카탈로그가 후보다. 지금도 그런 유저는 GENERAL 폴백으로
        // 전체를 보고 있었고, 여기서 막으면 그 유저만 무드 정렬에서 빠진다.
        // ponytail: IN 절을 조건부로 빼는 대신 SQL 안에서 OR로 처리하면 genre_id 인덱스를 못 타
        // 항상 전체 스캔이 된다(로컬 실측 6.9ms → 24.5ms). 그래서 자바에서 갈랐다.
        List<Long> genreIds = jdbcTemplate.queryForList(
                "SELECT genre_id FROM user_genres WHERE user_id = ?", Long.class, userId);
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

    /// 내적 스캔에서 몇 개를 받아올지. 비활성·하입·큐레이션 곡이 뒤에서 걸러지므로
    /// 필요한 수보다 넉넉히 받는다. 전체 벡터의 25%가 비활성 트랙이라 두 배로 잡았다.
    /// 스캔은 정렬 상위 K개만 만들면 되므로 K가 커져도 거의 안 비싸다(로컬 실측 100개 6.1ms, 400개 7.0ms).
    static int scanWindow(int limit, int offset) {
        return (limit + offset) * 2 + 20;
    }

    /// 유저가 최근 하입한 곡 중 벡터가 있는 것 SEEDS개를 벡터까지 한 번에 가져온다.
    /// (user_id, track_id) 유니크 제약이 만든 인덱스를 타므로 스캔이 아니다.
    private List<Seed> findSeeds(Long userId) {
        String cols = IntStream.range(0, DIMS).mapToObj(d -> "v.v" + d).collect(Collectors.joining(","));
        String sql = "SELECT v.track_id," + cols + " FROM track_vectors v "
                + "JOIN users_hype_tracks uht ON uht.track_id = v.track_id "
                + "WHERE uht.user_id = ? ORDER BY uht.user_hype_track_id DESC LIMIT " + SEEDS;
        return jdbcTemplate.query(sql, (rs, i) -> {
            float[] vector = new float[DIMS];
            for (int d = 0; d < DIMS; d++) {
                vector[d] = rs.getFloat("v" + d);
            }
            return new Seed(rs.getLong("track_id"), vector);
        }, userId);
    }

    /// **조인을 내적 스캔 안에 넣지 말 것.** 운영 실측으로 tracks 조인 +67ms, 하입 조인 +203ms다.
    /// 장르만 예외로 스캔 안에 있는데, `track_vectors.genre_id`가 tracks 복제본이라 조인이 아니다 —
    /// 곱셈 전에 행이 걸러져서 오히려 싸진다(로컬 실측 조인형 43.9ms vs 이 형태 6.6ms).
    /// 나머지 제외 조건은 상위 K개만 남은 바깥 쿼리에서 건다.
    ///
    /// 정렬 키에 track_id를 덧붙이는 건 동점 때문이다. 같은 유사도가 페이지마다 다른 순서로
    /// 나오면 OFFSET 페이징이 곡을 건너뛰거나 중복시킨다.
    static String orderSql(int seedCount, int genreCount, int seedArtistCount, int window) {
        String dots = IntStream.range(0, seedCount)
                .mapToObj(s -> "(" + IntStream.range(0, DIMS).mapToObj(d -> "v" + d + "*?").collect(Collectors.joining("+")) + ")")
                .collect(Collectors.joining(","));
        // 고른 장르가 없으면 필터를 통째로 뺀다 — 빈 IN 절은 후보를 0으로 만든다.
        String genreFilter = genreCount == 0 ? ""
                : "WHERE genre_id IN (" + placeholders(genreCount) + ") ";
        // 같은 아티스트는 정확하지만 이미 아는 걸 또 주는 것이다. 디깅 앱에선 추천이 아니다.
        String artistFilter = seedArtistCount == 0 ? ""
                : " AND (t.artist_id IS NULL OR t.artist_id NOT IN (" + placeholders(seedArtistCount) + "))";
        return "SELECT t.track_id FROM ("
                + "SELECT track_id, GREATEST(" + dots + ") AS sim FROM track_vectors "
                + genreFilter
                + "ORDER BY sim DESC, track_id LIMIT " + window + ") c "
                + "JOIN tracks t ON t.track_id = c.track_id "
                + "LEFT JOIN users_hype_tracks uht ON uht.track_id = t.track_id AND uht.user_id = ? "
                + "LEFT JOIN curation_tracks cur ON cur.track_id = t.track_id AND cur.is_active IS TRUE "
                + "WHERE t.is_active IS TRUE AND uht.user_hype_track_id IS NULL "
                + "AND cur.curation_track_id IS NULL AND c.sim < " + DUPLICATE_SIM + artistFilter
                + " ORDER BY c.sim DESC, t.track_id LIMIT ? OFFSET ?";
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
