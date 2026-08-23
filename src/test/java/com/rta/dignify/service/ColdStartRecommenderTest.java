package com.rta.dignify.service;

import com.rta.dignify.service.ColdStartRecommender.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// 콜드스타트에서 조용히 틀릴 수 있는 곳만 본다.
/// 제외 조건이 빠지거나 파라미터가 밀려도 예외는 안 나고 "그냥 이상한 첫 화면"이 될 뿐이다.
class ColdStartRecommenderTest {

    @Test
    @DisplayName("쿼리의 ? 개수가 넘기는 파라미터 개수와 맞는다")
    void 쿼리_플레이스홀더_개수() {
        for (int genreCount = 0; genreCount <= 13; genreCount++) {
            String sql = ColdStartRecommender.poolSql(genreCount);
            // 하입 조인 userId + 청취 조인 userId + 장르
            assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(2 + genreCount);
        }
    }

    @Test
    @DisplayName("장르를 안 고른 유저(게스트 포함)는 필터 없이 전 카탈로그가 후보다")
    void 장르_미선택() {
        // 빈 IN 절(`genre_id IN ()`)은 문법 오류다.
        assertThat(ColdStartRecommender.poolSql(0)).doesNotContain("genre_id IN");
        assertThat(ColdStartRecommender.poolSql(1)).contains("genre_id IN (?)");
        assertThat(ColdStartRecommender.poolSql(3)).contains("genre_id IN (?,?,?)");
    }

    @Test
    @DisplayName("인기 집계와 제외 조건이 전부 걸려 있다")
    void 풀_조건() {
        String sql = ColdStartRecommender.poolSql(0);

        // 하입 가중치가 빠지면 청취 수가 순위를 지배한다 — 청취는 선택이 아니라 노출의 함수다.
        assertThat(sql).contains("SELECT track_id, " + ColdStartRecommender.HYPE_WEIGHT + " AS w FROM users_hype_tracks");
        // 큐레이션 곡은 전 유저 동일 노출이라 카운트가 인기가 아니라 편성이고, 세트에서 이미 봤다.
        assertThat(sql).contains("cur.curation_track_id IS NULL");
        // 이 유저가 하입·청취한 곡 제외. 재방문 중복 + 집계 자기오염을 한 번에 막는다.
        assertThat(sql).contains("uht.user_hype_track_id IS NULL");
        assertThat(sql).contains("lt.listened_track_id IS NULL");
        assertThat(sql).contains("t.is_active IS TRUE");
        // 게스트는 userId가 null이라 형을 못 정한다. 캐스팅이 빠지면 게스트 요청만 터진다.
        assertThat(sql).contains("uht.user_id = CAST(? AS BIGINT)");
        assertThat(sql).contains("lt.user_id = CAST(? AS BIGINT)");
        // 동점 순서가 흔들리면 셔플 결과가 페이지마다 달라져 곡이 겹치거나 빠진다.
        assertThat(sql).contains("ORDER BY p.score DESC, v.track_id LIMIT " + ColdStartRecommender.POOL);
    }

    @Test
    @DisplayName("흩뿌리기는 이미 고른 것들과 가장 먼 곡을 다음에 놓는다")
    void 흩뿌리기_순서() {
        Candidate first = candidate(1L, 1.0f, 0.0f);
        Candidate copy = candidate(2L, 1.0f, 0.0f);      // first와 유사도 1 — 사실상 같은 곡
        Candidate right = candidate(3L, 0.0f, 1.0f);     // 유사도 0
        Candidate opposite = candidate(4L, -1.0f, 0.0f); // 유사도 -1 — 가장 멀다

        List<Long> ordered = ColdStartRecommender.spread(List.of(first, copy, right, opposite), 4);

        // 평균이 아니라 최대 유사도로 고른다. 이미 고른 것 하나와만 닮아도 유저 눈엔 중복이다.
        assertThat(ordered).containsExactly(1L, 4L, 3L, 2L);
    }

    @Test
    @DisplayName("흩뿌리기는 요청한 수만큼, 중복 없이 낸다")
    void 흩뿌리기_개수() {
        List<Candidate> candidates = java.util.stream.IntStream.range(0, ColdStartRecommender.CANDIDATES)
                .mapToObj(i -> candidate((long) i, (float) Math.cos(i), (float) Math.sin(i)))
                .toList();

        List<Long> ordered = ColdStartRecommender.spread(candidates, ColdStartRecommender.WINDOW);

        assertThat(ordered).hasSize(ColdStartRecommender.WINDOW).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("콜드스타트 창을 벗어나거나 걸치는 페이지는 받지 않는다")
    void 창_경계() {
        // 짧은 페이지를 돌려주면 FeedService가 '장르 풀 소진'으로 읽어 커서를 GENERAL로 넘긴다.
        // 그래서 DB를 보기 전에 잘라낸다 — jdbcTemplate이 null이어도 이 경로는 안 건드린다.
        ColdStartRecommender recommender = new ColdStartRecommender(null);

        assertThat(recommender.orderedTrackIds(1L, 10, ColdStartRecommender.WINDOW, 7)).isEmpty();
        assertThat(recommender.orderedTrackIds(1L, 10, ColdStartRecommender.WINDOW - 5, 7)).isEmpty();
        assertThat(recommender.orderedTrackIds(1L, 0, 0, 7)).isEmpty();
    }

    private static Candidate candidate(Long trackId, float x, float y) {
        float[] vector = new float[MoodRecommender.DIMS];
        vector[0] = x;
        vector[1] = y;
        return new Candidate(trackId, vector);
    }
}
