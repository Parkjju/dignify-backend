package com.rta.dignify.controller;

import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/feed")
@RestController
public class FeedController {
    private final FeedService feedService;

    @GetMapping
    public FeedResponse getFeedList(@AuthenticationPrincipal Long userId, @RequestParam(required = false) String cursor) {
        return feedService.getFeedList(userId, cursor);
    }
}
