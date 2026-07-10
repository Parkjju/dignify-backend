package com.rta.dignify.controller;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.service.cron.CronService;
import com.rta.dignify.service.cron.KoEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class CronController {

    @Value("${cron.secret}")
    private String cronSecret;

    private final CronService cronService;
    private final KoEnrichmentService koEnrichmentService;

    @PostMapping("/internal/cron/collect")
    public ResponseEntity<Void> processCronJob(
            @RequestHeader("X-Cron-Secret") String requestSecret,
            @RequestParam long endIndex) throws InterruptedException {
        if (!cronSecret.equals(requestSecret)) {
            throw new BusinessException(ErrorCode.CRON_SECRET_INVALID);
        }

        cronService.callItunesAPI("track_collect", endIndex);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/internal/cron/collect-artist")
    public ResponseEntity<Integer> collectByArtist(
            @RequestHeader("X-Cron-Secret") String requestSecret,
            @RequestParam String name) {
        if (!cronSecret.equals(requestSecret)) {
            throw new BusinessException(ErrorCode.CRON_SECRET_INVALID);
        }

        int saved = cronService.collectByArtist(name);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/internal/cron/enrich-ko")
    public ResponseEntity<Void> processKoEnrichment(
            @RequestHeader("X-Cron-Secret") String requestSecret) throws InterruptedException {
        if (!cronSecret.equals(requestSecret)) {
            throw new BusinessException(ErrorCode.CRON_SECRET_INVALID);
        }

        koEnrichmentService.enrichKo();
        return ResponseEntity.accepted().build();
    }
}
