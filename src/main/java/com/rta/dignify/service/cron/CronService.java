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

    @Async
    public void callItunesAPI(String jobName, long endIndex) throws InterruptedException {
        int totalProcessed = 0;
        int batchCount = 0;

        while (true) {
            try {
                CronBatchService.ProcessResult result = cronBatchService.processBatch(jobName);
                batchCount++;
                totalProcessed += result.processedSize();
                log.info("Batch {} done — processed: {}, lastId: {}", batchCount, result.processedSize(), result.lastProcessedId());

                if (result.lastProcessedId() >= endIndex) {
                    log.info("Cron job '{}' reached endIndex {}. total processed: {}", jobName, endIndex, totalProcessed);
                    break;
                }

                Thread.sleep(30000);
            } catch (ResourceAccessException e) {
                log.warn("iTunes API connection dropped after {} batches: {}", batchCount, e.getMessage());
                break;
            }
        }

        log.info("Cron job '{}' finished — total processed: {}", jobName, totalProcessed);
    }
}
