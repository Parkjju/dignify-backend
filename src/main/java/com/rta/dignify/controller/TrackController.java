package com.rta.dignify.controller;

import com.rta.dignify.service.HypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/tracks")
@RestController
public class TrackController {

    private final HypeService hypeService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{trackId}/hype")
    public void registerHype(@AuthenticationPrincipal Long userId, @PathVariable Long trackId) {
        hypeService.registerHype(userId, trackId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{trackId}/hype")
    public void deleteHype(@AuthenticationPrincipal Long userId, @PathVariable Long trackId) {
        hypeService.deleteHype(userId, trackId);
    }
}
