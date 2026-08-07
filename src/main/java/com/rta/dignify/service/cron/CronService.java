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
    // 이름이 정확히 일치하는 아티스트가 딱 한 명일 때만 수집한다. 0명(로마자 표기 등으로 못 찾음)이거나
    // 2명 이상(동명이인)이면 후보를 로그로 뱉고 중단 — 어느 쪽인지는 사람이 보고 collect-artist-id로 재실행.
    public CronBatchService.SaveResult collectByArtist(String artistName) {
        String name = artistName.trim();
        log.info("collect-artist '{}' resolving artistId...", name);
        List<ItunesItem> candidates = iTunesAPIClient.searchArtists(name);
        List<ItunesItem> exact = candidates.stream()
                .filter(a -> name.equalsIgnoreCase(a.artistName()))
                .toList();

        if (exact.size() != 1) {
            log.warn("collect-artist '{}' ABORTED — 이름이 정확히 일치하는 아티스트 {}명. 아래에서 고른 뒤 재실행: ./run-cron.sh collect-artist-id <artistId>",
                    name, exact.size());
            if (candidates.isEmpty()) {
                log.warn("collect-artist '{}'   후보 없음 — iTunes에 없는 이름이거나 표기가 다름", name);
            }
            candidates.forEach(a -> log.warn("collect-artist '{}'   artistId={} name='{}' genre={} {}",
                    name, a.artistId(), a.artistName(), a.primaryGenreName(), a.artistLinkUrl()));
            return CronBatchService.SaveResult.empty();
        }

        ItunesItem artist = exact.get(0);
        log.info("collect-artist '{}' resolved → artistId={} genre={}", name, artist.artistId(), artist.primaryGenreName());
        return collectByArtistId(artist.artistId());
    }

    // 동명이인 때문에 중단된 건을 사람이 artistId로 지정해 수집한다.
    public CronBatchService.SaveResult collectByArtistId(long artistId) {
        List<ItunesItem> items = iTunesAPIClient.lookupSongsByArtistId(artistId);
        log.info("collect-artist artistId={} found {} tracks with preview — saving...", artistId, items.size());
        CronBatchService.SaveResult result = cronBatchService.saveItems(items);
        log.info("collect-artist artistId={} finished — {}", artistId, result);
        return result;
    }
}
