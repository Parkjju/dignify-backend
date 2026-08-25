package com.rta.dignify.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/// 추천 기준으로 고정할 곡. **빈 배열이 곧 고정 해제**라 별도 삭제 엔드포인트를 두지 않는다.
///
/// 상한은 MoodRecommender.SEEDS와 같아야 한다. 더 받아 봐야 스캔이 그 수만 쓰기 때문에,
/// 넘겨받고 조용히 버리면 유저는 고른 곡이 반영된 줄 안다.
public record SeedTracksUpdateRequest(@NotNull @Size(max = 3) List<Long> trackIds) {
}
