package com.rta.dignify.controller;

import com.rta.dignify.dto.cron.CronJobResult;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.service.cron.CronService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CronController {

    @Value("${cron.secret}")
    private String cronSecret;

    private final CronService cronService;

    @PostMapping("/internal/cron/collect")
    public CronJobResult processCronJob(@RequestHeader("X-Cron-Secret") String requestSecret) throws InterruptedException {
        if (!cronSecret.equals(requestSecret)) {
            throw new BusinessException(ErrorCode.CRON_SECRET_INVALID);
        }

        return cronService.callItunesAPI("track_collect");
    }
}
