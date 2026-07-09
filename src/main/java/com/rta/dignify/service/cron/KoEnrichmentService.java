package com.rta.dignify.service.cron;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.dto.itunes.ItunesItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class KoEnrichmentService {
    private static final int BATCH_SIZE = 190;

    private final KoEnrichmentBatchService koEnrichmentBatchService;
    private final ITunesAPIClient iTunesAPIClient;

    // ko_checked=false 트랙을 배치로 훑으며 KR lookup 결과로 ko 컬럼을 채운다. 큐가 비면 종료.
    @Async
    public void enrichKo() throws InterruptedException {
        int totalChecked = 0;
        int totalMatched = 0;
        int batchCount = 0;
        long total = koEnrichmentBatchService.countUnchecked();
        log.info("Ko enrichment starting — {} tracks to process", total);

        while (true) {
            try {
                // HTTP 호출은 트랜잭션 밖에서. DB 커넥션을 쥔 채 iTunes 응답을 기다리지 않도록 분리.
                List<String> externalIds = koEnrichmentBatchService.peekUncheckedExternalIds(BATCH_SIZE);
                if (externalIds.isEmpty()) {
                    log.info("Ko enrichment queue drained.");
                    break;
                }

                List<ItunesItem> krItems = iTunesAPIClient.lookupKrByTrackIds(externalIds);
                int matched = koEnrichmentBatchService.applyKo(externalIds, krItems);

                batchCount++;
                totalChecked += externalIds.size();
                totalMatched += matched;
                long percent = total > 0 ? totalChecked * 100 / total : 100;
                log.info("Ko batch {} done — checked: {}, matched: {} — progress: {}/{} ({}%)",
                        batchCount, externalIds.size(), matched, totalChecked, total, percent);

                Thread.sleep(30000);
            } catch (ResourceAccessException e) {
                log.warn("iTunes API connection dropped after {} ko batches: {}", batchCount, e.getMessage());
                break;
            } catch (DataAccessException e) {
                log.error("DB connection lost after {} ko batches: {}", batchCount, e.getMessage());
                break;
            }
        }

        log.info("Ko enrichment finished — checked: {}, matched: {}", totalChecked, totalMatched);
    }
}
