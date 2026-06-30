package com.rta.dignify.service.cron;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@RequiredArgsConstructor
@Service
public class CronService {
    private final CronBatchService cronBatchService;
    private static final int MAX_BATCHES_PER_RUN = 100;

    @Async
    public void callItunesAPI(String jobName) throws InterruptedException {
        int processedSize = 0;

        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            try {
                CronBatchService.ProcessResult result = cronBatchService.processBatch(jobName);
                processedSize += result.processedSize();
                log.info("Batch {}/{} done — processed: {}, lastId: {}", i + 1, MAX_BATCHES_PER_RUN, result.processedSize(), result.lastProcessedId());
                Thread.sleep(30000);
            } catch (ResourceAccessException e) {
                log.warn("iTunes API connection dropped after {} batches: {}", i, e.getMessage());
                break;
            }
        }

        log.info("Cron job '{}' finished — total processed: {}", jobName, processedSize);
    }
}
