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
import java.util.stream.LongStream;

@Slf4j
@RequiredArgsConstructor
@Service
public class CronService {
    private final CronBatchService cronBatchService;
    private final ITunesAPIClient iTunesAPIClient;

    @Async
    public void callItunesAPI(String jobName, long endIndex) throws InterruptedException {
        int totalProcessed = 0;
        int batchCount = 0;

        while (true) {
            try {
                // 외부 API 호출은 트랜잭션 밖에서. DB 커넥션을 쥔 채 iTunes 응답을 기다리지 않도록 분리.
                long startId = cronBatchService.peekNextStartId(jobName);
                List<ItunesItem> items = iTunesAPIClient.lookup(
                        LongStream.range(startId, startId + 200).boxed().toList());

                CronBatchService.ProcessResult result = cronBatchService.processBatch(jobName, items);
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
            } catch (DataAccessException e) {
                log.error("DB connection lost after {} batches: {}", batchCount, e.getMessage());
                break;
            }
        }

        log.info("Cron job '{}' finished — total processed: {}", jobName, totalProcessed);
    }

    // 아티스트명 기반 수동 collect. 단발 검색이라 @Async/루프/cronState 없이 동기 처리.
    // 저장한 트랙은 curation_tracks에도 등록 → 아티스트 collect는 곧 큐레이션.
    public int collectByArtist(String artistName) {
        log.info("collect-artist '{}' searching iTunes...", artistName);
        List<ItunesItem> items = iTunesAPIClient.searchByArtist(artistName);
        log.info("collect-artist '{}' found {} tracks with preview — saving...", artistName, items.size());
        int saved = cronBatchService.saveItems(items);
        List<String> externalIds = items.stream().map(item -> String.valueOf(item.trackId())).toList();
        int curated = cronBatchService.curateByExternalIds(externalIds);
        log.info("collect-artist '{}' finished — found: {}, saved: {}, curated: {}, skipped(dup/no-genre): {}",
                artistName, items.size(), saved, curated, items.size() - saved);
        return saved;
    }
}
