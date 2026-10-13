package com.rta.dignify.service;

import com.rta.dignify.domain.FeedState;
import com.rta.dignify.service.MoodRecommender.Seed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// 무드 정렬에서 조용히 틀릴 수 있는 곳만 본다.
/// 파라미터가 한 칸 밀리거나 제외 조건이 빠져도 예외는 안 나고 "그냥 이상한 순서"가 될 뿐이라
/// 눈으로는 못 잡는다.
class MoodRecommenderTest {

    @Test
    @DisplayName("파라미터는 seed 순서 → 차원 순서로 깔린다")
    void 파라미터_순서() {
        float[] first = new float[MoodRecommender.DIMS];
        float[] second = new float[MoodRecommender.DIMS];
        first[0] = 1.5f;
        first[MoodRecommender.DIMS - 1] = 2.5f;
        second[0] = 3.5f;

        Object[] params = MoodRecommender.flatten(List.of(new Seed(1L, first), new Seed(2L, second)));

        assertThat(params).hasSize(MoodRecommender.DIMS * 2);
        assertThat(params[0]).isEqualTo(1.5f);
        assertThat(params[MoodRecommender.DIMS - 1]).isEqualTo(2.5f);
        assertThat(params[MoodRecommender.DIMS]).isEqualTo(3.5f);
    }

    @Test
    @DisplayName("쿼리의 ? 개수가 넘기는 파라미터 개수와 맞는다")
    void 쿼리_플레이스홀더_개수() {
        for (int seedCount = 1; seedCount <= MoodRecommender.SEEDS; seedCount++) {
            for (int droppedCount = 0; droppedCount <= FeedState.MAX_DROPPED; droppedCount++) {
                for (int genreCount = 0; genreCount <= 13; genreCount++) {
                    String sql = MoodRecommender.orderSql(seedCount, droppedCount, genreCount);
                    // 내적(시드+빠진 시드)×차원 + 장르 + θ + 빠진 시드별 θ + limit + userId×2 + limit
                    long expected = (long) (seedCount + droppedCount) * MoodRecommender.DIMS
                            + genreCount + 1 + droppedCount + 1 + 2 + 1;
                    assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    @DisplayName("한 줄 정렬이다 — 유사도 내림차순, 동점은 track_id, 번갈아 꺼내기 없음")
    void 정렬_키() {
        String sql = MoodRecommender.orderSql(3, 2, 1);
        assertThat(sql).endsWith("ORDER BY c.sim DESC, c.track_id LIMIT ?");
        // 시드별 번갈아 꺼내기가 페이지 간 중복을 만들었다(KAN-23).
        assertThat(sql).doesNotContain("ROW_NUMBER");
        // 내적을 담은 층을 플래너가 펼치지 못하게 막는다. 펼치면 내적을 WHERE에서 또 곱해 27ms → 60ms다.
        assertThat(sql).contains(" OFFSET 0) x ");
        // 내적은 시드·빠진 시드마다 한 번씩만 적힌다. 두 번 적으면 스캔 비용이 두 배다.
        assertThat(sql.split("v0\\*\\?", -1)).hasSize(3 + 2 + 1);
        // 빠진 시드마다 그 시드와의 유사도로 거른다.
        assertThat(sql).contains("AND d0 < ?").contains("AND d1 < ?");
    }

    @Test
    @DisplayName("장르·활성은 스캔 안에서, 하입·큐레이션 제외는 스캔 밖에서 건다")
    void 제외_조건_위치() {
        String sql = MoodRecommender.orderSql(3, 0, 1);
        int scanEnd = sql.indexOf(") c ");

        // 복제 컬럼이라 곱셈 전에 행이 걸러진다(실측 43.9ms → 6.6ms, 활성 30 → 23ms).
        assertThat(sql.indexOf("genre_id IN")).isBetween(0, scanEnd);
        assertThat(sql.indexOf("is_active IS TRUE")).isBetween(0, scanEnd);
        assertThat(sql).doesNotContain("JOIN tracks");
        // 하입·큐레이션 조인이 스캔 안에 들어가면 +203ms다.
        assertThat(sql.indexOf("LEFT JOIN users_hype_tracks")).isGreaterThan(scanEnd);
        assertThat(sql.indexOf("LEFT JOIN curation_tracks")).isGreaterThan(scanEnd);
        // 리마스터·커버(유사도 0.95↑)는 정렬 앞자리를 통째로 먹는다.
        assertThat(sql).contains(" < 0.95");
        assertThat(sql).doesNotContain("artist_id");
    }

    @Test
    @DisplayName("장르를 안 고른 유저는 필터 없이 전 카탈로그가 후보다")
    void 장르_미선택() {
        // 빈 IN 절(`genre_id IN ()`)은 문법 오류이고, 있는 척 넘기면 후보가 0이 돼
        // 그 유저만 조용히 무드 정렬에서 빠진다.
        assertThat(MoodRecommender.orderSql(3, 0, 0)).doesNotContain("genre_id IN");
        assertThat(MoodRecommender.orderSql(3, 0, 1)).contains("genre_id IN (?)");
        assertThat(MoodRecommender.orderSql(3, 0, 3)).contains("genre_id IN (?,?,?)");
    }

    @Test
    @DisplayName("피드 상태: 시드가 전부 바뀌면 처음부터, 일부만 바뀌면 빠진 시드를 θ와 함께 기억한다")
    void 피드_상태() {
        FeedState first = FeedState.begin(null, List.of(1L, 2L, 3L));
        assertThat(first.theta()).isEqualTo(1.0);

        // 셋째 칸만 바꿨다 → 3번이 그때의 θ로 dropped에 남는다. θ는 이어 간다.
        FeedState swapped = FeedState.begin(first.sentDownTo(0.8), List.of(1L, 2L, 4L));
        assertThat(swapped.theta()).isEqualTo(0.8);
        assertThat(swapped.dropped()).containsExactly(new FeedState.Dropped(3L, 0.8));
        assertThat(swapped.startSeeds()).containsExactly(1L, 2L, 3L);

        // 셋째 칸을 여러 번 바꿔도 초기화하지 않고 오래된 것부터 잊는다.
        FeedState state = swapped;
        for (long seed = 5; seed < 12; seed++) {
            state = FeedState.begin(state.sentDownTo(0.5), List.of(1L, 2L, seed));
        }
        assertThat(state.theta()).isEqualTo(0.5);
        assertThat(state.dropped()).hasSize(FeedState.MAX_DROPPED);
        assertThat(state.dropped().get(FeedState.MAX_DROPPED - 1).seedId()).isEqualTo(10L);

        // 하입으로 한 칸씩 밀려 start_seeds와 하나도 안 겹치면 초기화.
        FeedState shifted = FeedState.begin(first.sentDownTo(0.7), List.of(2L, 3L, 4L));
        shifted = FeedState.begin(shifted.sentDownTo(0.6), List.of(3L, 4L, 5L));
        assertThat(shifted.theta()).isEqualTo(0.6);
        shifted = FeedState.begin(shifted.sentDownTo(0.5), List.of(4L, 5L, 6L));
        assertThat(shifted.theta()).isEqualTo(1.0);
        assertThat(shifted.dropped()).isEmpty();
    }

    @Test
    @DisplayName("근거는 내적이 가장 큰 시드다 — 정렬에 쓴 최댓값을 만든 그 곡")
    void 근거_시드() {
        MoodRecommender.Seed pop = new MoodRecommender.Seed(1L, new float[]{1, 0});
        MoodRecommender.Seed ballad = new MoodRecommender.Seed(2L, new float[]{0, 1});
        MoodRecommender.Seed opposite = new MoodRecommender.Seed(3L, new float[]{-1, 0});

        assertThat(MoodRecommender.bestSeed(new float[]{0.9f, 0.1f}, List.of(pop, ballad, opposite))).isEqualTo(1L);
        assertThat(MoodRecommender.bestSeed(new float[]{0.1f, 0.9f}, List.of(pop, ballad, opposite))).isEqualTo(2L);
        // 전부 반대쪽이어도 하나는 고른다 — 정렬이 이미 그 값으로 됐으므로 근거는 항상 존재한다.
        assertThat(MoodRecommender.bestSeed(new float[]{-1, 0}, List.of(pop, ballad, opposite))).isEqualTo(3L);
    }
}
