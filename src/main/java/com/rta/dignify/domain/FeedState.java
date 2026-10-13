package com.rta.dignify.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// 무드 피드를 어디까지 보냈는지. 커서가 아니라 `users.feed_state`에 둔다 — 앱이 시드를 바꾸면
/// 커서를 지워 버려서(iOS `FeedView.savedFeedCursor = ""`), 커서에 두면 처음부터 다시 나간다.
///
/// 위치는 순번이 아니라 **점수**다. "θ까지 보냈다"를 기억하고 다음은 `점수 < θ`부터라,
/// 하입·수집으로 목록이 한 칸 밀려도 경계가 안 움직인다. 설계 전문은 Jira KAN-23.
///
/// @param startSeeds θ를 1.0에서 출발시킨 시점의 시드. 현재 시드와 하나도 안 겹치면 초기화한다
/// @param lastSeeds  직전 요청의 시드. 이번에 빠진 시드를 찾는 용도다
/// @param theta      마지막으로 보낸 곡 점수
/// @param dropped    빠진 시드와 빠질 때의 θ. 그 시드 덕분에 이미 나간 곡을 다시 안 내려고 둔다
public record FeedState(List<Long> startSeeds, List<Long> lastSeeds, double theta, List<Dropped> dropped) {
    /// 넘치면 가장 오래된 것부터 잊는다. **초기화하지 않는다** — A·B를 고정한 채 셋째 칸만 여러 번
    /// 바꾸는 유저에게 초기화는 A·B 상위 곡을 처음부터 다시 내보내는 것이다.
    public static final int MAX_DROPPED = 5;

    public record Dropped(long seedId, double theta) {
    }

    /// 요청 시작 시점의 상태. 비교 기준이 직전 시드가 아니라 `startSeeds`인 건, 하입으로 한 칸씩
    /// 바뀌는 유저(ABC→BCD→CDE→DEF)도 언젠가 초기화되게 하려는 것이다.
    public static FeedState begin(FeedState prev, List<Long> seeds) {
        if (prev == null || Collections.disjoint(prev.startSeeds, seeds)) {
            return new FeedState(seeds, seeds, 1.0, List.of());
        }
        List<Dropped> dropped = new ArrayList<>(prev.dropped);
        for (Long seed : prev.lastSeeds) {
            if (!seeds.contains(seed)) {
                dropped.add(new Dropped(seed, prev.theta));
            }
        }
        if (dropped.size() > MAX_DROPPED) {
            dropped = dropped.subList(dropped.size() - MAX_DROPPED, dropped.size());
        }
        return new FeedState(prev.startSeeds, seeds, prev.theta, List.copyOf(dropped));
    }

    public FeedState sentDownTo(double theta) {
        return new FeedState(startSeeds, lastSeeds, theta, dropped);
    }
}
