package com.rta.dignify.controller;

import com.rta.dignify.dto.feed.CurationResponse;
import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.service.FeedService;
import com.rta.dignify.service.NewReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/feed")
@RestController
public class FeedController {
    private final FeedService feedService;
    private final NewReleaseService newReleaseService;

    @GetMapping
    public FeedResponse getFeedList(@AuthenticationPrincipal Long userId, @RequestParam(required = false) String cursor) {
        return feedService.getFeedList(userId, cursor);
    }

    /// 이번 주 큐레이션 세트. 게스트도 볼 수 있어야 하므로 userId가 null이어도 동작한다.
    @GetMapping("/curation")
    public CurationResponse getCurationFeed(@AuthenticationPrincipal Long userId) {
        return feedService.getCurationFeed(userId);
    }

    @GetMapping("/search")
    public FeedResponse searchFeedList(@AuthenticationPrincipal Long userId, @RequestParam(required = false) String cursor, @RequestParam String q) {
        return feedService.searchFeedList(userId, cursor, q);
    }

    /// 좋아요한 아티스트의 최근 7일 신곡(안 들은 것). 피드 맨 앞에 붙인다. 게스트는 빈 목록.
    @GetMapping("/new-releases")
    public FeedResponse getNewReleases(@AuthenticationPrincipal Long userId) {
        return feedService.getTracks(newReleaseService.feedTrackIds(userId));
    }

    /// 알림이 실어 온 곡(신곡·주간 추천)을 그 순서대로. 내려간 곡은 빠진다.
    @GetMapping("/tracks")
    public FeedResponse getTracks(@RequestParam List<Long> ids) {
        return feedService.getTracks(ids.stream().limit(100).toList());
    }

    /// 이 곡들과 무드가 가까운 곡(신곡 연관 피드). 평소 피드의 진행 위치는 안 바뀐다.
    @GetMapping("/related")
    public FeedResponse getRelated(@AuthenticationPrincipal Long userId, @RequestParam List<Long> seeds,
                                   @RequestParam(defaultValue = "30") int limit) {
        return feedService.getRelated(userId, seeds, limit);
    }
}
