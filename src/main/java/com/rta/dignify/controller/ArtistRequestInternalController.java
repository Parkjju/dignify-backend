package com.rta.dignify.controller;

import com.rta.dignify.dto.artistrequest.ResolveRequest;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.service.ArtistRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ArtistRequestInternalController {
    private final ArtistRequestService artistRequestService;

    @Value("${cron.secret}")
    private String cronSecret;

    @PostMapping("/internal/artist-requests/{id}/resolve")
    public ResponseEntity<Void> resolve(@RequestHeader("X-Cron-Secret") String secret, @PathVariable Long id, @RequestBody ResolveRequest body) {
        if (!cronSecret.equals(secret)) throw new BusinessException(ErrorCode.CRON_SECRET_INVALID);
        artistRequestService.resolve(id, body.status(), body.cancelReason());
        return ResponseEntity.ok().build();
    }
}
