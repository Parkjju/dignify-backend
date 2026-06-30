package com.rta.dignify.controller;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.service.cron.CronService;
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
}
