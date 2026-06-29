package com.rta.dignify.controller;

import com.rta.dignify.dto.track.TrackDetailResponse;
import com.rta.dignify.service.HypeService;
import com.rta.dignify.service.ListenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/tracks")
@RestController
public class TrackController {

    private final HypeService hypeService;
    private final ListenService listenService;

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

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{trackId}/listen")
    public void recordListenedTrack(@AuthenticationPrincipal Long userId, @PathVariable Long trackId) {
        listenService.recordListenedTrack(userId, trackId);
    }

    @GetMapping("/{trackId}")
    public TrackDetailResponse getTrackDetails(@PathVariable Long trackId) {
        return hypeService.getTrackDetails(trackId);
    }
}
