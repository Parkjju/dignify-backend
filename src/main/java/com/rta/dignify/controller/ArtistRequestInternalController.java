package com.rta.dignify.controller;

import com.rta.dignify.dto.artistrequest.ResolveRequest;
import com.rta.dignify.global.security.InternalSecrets;
import com.rta.dignify.service.ArtistRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ArtistRequestInternalController {
    private final ArtistRequestService artistRequestService;
    private final InternalSecrets internalSecrets;

    @PostMapping("/internal/artist-requests/{id}/resolve")
    public ResponseEntity<Void> resolve(@RequestHeader("X-Cron-Secret") String secret, @PathVariable Long id, @RequestBody ResolveRequest body) {
        internalSecrets.verifyAdmin(secret);
        artistRequestService.resolve(id, body.status(), body.cancelReason());
        return ResponseEntity.ok().build();
    }
}
