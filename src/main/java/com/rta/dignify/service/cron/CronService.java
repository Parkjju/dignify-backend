package com.rta.dignify.service.cron;

import com.rta.dignify.dto.cron.CronJobResult;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class CronService {
    private final CronBatchService cronBatchService;
    private static final Integer MAX_BATCHES_PER_RUN = 100;

    public CronJobResult callItunesAPI(String jobName) throws InterruptedException {
        Instant start = Instant.now();
        CronBatchService.ProcessResult processResult = null;
        int processedSize = 0;

        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            try {
                processResult = cronBatchService.processBatch(jobName);
                processedSize += processResult.processedSize();
                Thread.sleep(30000);
            } catch (ResourceAccessException e) {
                log.warn("iTunes API connection dropped after {} batches: {}", i, e.getMessage());
                break;
            }
        }

        if (processResult == null) { throw new BusinessException(ErrorCode.CRON_JOB_FAILED); }
        Instant end = Instant.now();

        return new CronJobResult(processedSize, processResult.lastProcessedId(), Duration.between(start, end).getSeconds());
    }
}
