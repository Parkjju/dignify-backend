package com.rta.dignify.service;

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
            for (int genreCount = 0; genreCount <= 13; genreCount++) {
                for (int artistCount = 0; artistCount <= seedCount; artistCount++) {
                    String sql = MoodRecommender.orderSql(seedCount, genreCount, artistCount, 40);
                    // 내적(seed×차원) + 장르 + 하입 조인 userId + 아티스트 + limit + offset
                    long expected = (long) seedCount * MoodRecommender.DIMS + genreCount + 1 + artistCount + 2;
                    assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    @DisplayName("정렬은 유사도 내림차순이고, 동점은 track_id로 갈린다")
    void 정렬_키() {
        String sql = MoodRecommender.orderSql(3, 1, 1, 40);
        // 동점 정렬이 페이지마다 흔들리면 OFFSET 페이징이 곡을 건너뛰거나 중복시킨다.
        assertThat(sql).contains("ORDER BY sim DESC, track_id LIMIT 40");
        assertThat(sql).contains("ORDER BY c.sim DESC, t.track_id LIMIT ? OFFSET ?");
        // 내적을 SELECT와 ORDER BY에서 두 번 계산하면 스캔 비용이 두 배가 된다.
        assertThat(sql).containsOnlyOnce("GREATEST(");
    }

    @Test
    @DisplayName("장르는 스캔 안에서, 나머지 제외는 스캔 밖에서 건다")
    void 제외_조건_위치() {
        String sql = MoodRecommender.orderSql(3, 1, 2, 40);
        int scanEnd = sql.indexOf(") c ");

        // 장르가 스캔 안에 있어야 곱셈 전에 행이 걸러진다(실측 43.9ms → 6.6ms).
        assertThat(sql.indexOf("genre_id IN")).isBetween(0, scanEnd);
        // 하입·큐레이션·활성 조인이 스캔 안에 들어가면 +203ms다.
        assertThat(sql.indexOf("users_hype_tracks")).isGreaterThan(scanEnd);
        assertThat(sql.indexOf("curation_tracks")).isGreaterThan(scanEnd);
        assertThat(sql.indexOf("t.is_active")).isGreaterThan(scanEnd);
        // 리마스터·커버(유사도 0.95↑)와 같은 아티스트는 정렬 앞자리를 통째로 먹는다.
        assertThat(sql).contains("c.sim < 0.95");
        assertThat(sql).contains("t.artist_id NOT IN (?,?)");
    }

    @Test
    @DisplayName("장르를 안 고른 유저는 필터 없이 전 카탈로그가 후보다")
    void 장르_미선택() {
        // 빈 IN 절(`genre_id IN ()`)은 문법 오류이고, 있는 척 넘기면 후보가 0이 돼
        // 그 유저만 조용히 무드 정렬에서 빠진다.
        assertThat(MoodRecommender.orderSql(3, 0, 1, 40)).doesNotContain("genre_id IN");
        assertThat(MoodRecommender.orderSql(3, 1, 1, 40)).contains("genre_id IN (?)");
        assertThat(MoodRecommender.orderSql(3, 3, 1, 40)).contains("genre_id IN (?,?,?)");
    }

    @Test
    @DisplayName("스캔 창은 깊이 스크롤해도 필요한 수보다 넉넉하다")
    void 스캔_창() {
        // 전체 벡터의 25%가 비활성 트랙이라 필터로 걸러진다. 창이 빠듯하면 페이지가 짧아지고,
        // 짧아진 페이지는 '장르 소진'으로 오해돼 general 폴백으로 넘어간다.
        assertThat(MoodRecommender.scanWindow(10, 0)).isGreaterThanOrEqualTo(30);
        assertThat(MoodRecommender.scanWindow(10, 100)).isGreaterThanOrEqualTo(150);
    }
}
