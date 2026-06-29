package com.rta.dignify.service;

import com.rta.dignify.dto.cron.CronJobResult;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
@Service
public class CronService {
    private final CronBatchService cronBatchService;
    private static final Integer MAX_BATCHES_PER_RUN = 1000;

    public CronJobResult callItunesAPI(String jobName) throws InterruptedException {
        Instant start = Instant.now();
        CronBatchService.ProcessResult processResult = null;
        int processedSize = 0;

        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            processResult = cronBatchService.processBatch(jobName);
            processedSize += processResult.processedSize();
            Thread.sleep(3000);
        }

        if (processResult == null) { throw new BusinessException(ErrorCode.CRON_JOB_FAILED); }
        Instant end = Instant.now();

        return new CronJobResult(processedSize, processResult.lastProcessedId(), Duration.between(start, end).getSeconds());
    }
}
