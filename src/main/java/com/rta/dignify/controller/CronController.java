package com.rta.dignify.controller;

import com.rta.dignify.domain.PushVariant;
import com.rta.dignify.global.security.InternalSecrets;
import com.rta.dignify.service.NewReleaseService;
import com.rta.dignify.service.WeeklyPickService;
import com.rta.dignify.service.cron.CronBatchService;
import com.rta.dignify.service.cron.CronService;
import com.rta.dignify.service.cron.KoEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/// 두 종류가 섞여 있다. collect/enrich-ko는 몇 시간 도는 배치라 로컬 스크립트만 부르고(크론 시크릿),
/// collect-artist류는 어드민 화면의 [수집] 버튼도 부른다(어드민 시크릿).
@RequiredArgsConstructor
@RestController
public class CronController {

    private final CronService cronService;
    private final KoEnrichmentService koEnrichmentService;
    private final InternalSecrets internalSecrets;
    private final NewReleaseService newReleaseService;
    private final WeeklyPickService weeklyPickService;

    @PostMapping("/internal/cron/collect")
    public ResponseEntity<Void> processCronJob(
            @RequestHeader("X-Cron-Secret") String requestSecret,
            @RequestParam long endIndex) throws InterruptedException {
        internalSecrets.verifyCron(requestSecret);

        cronService.callItunesAPI("track_collect", endIndex);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/internal/cron/collect-artist")
    public ResponseEntity<CronBatchService.SaveResult> collectByArtist(
            @RequestHeader("X-Cron-Secret") String requestSecret,
            @RequestParam String name) {
        internalSecrets.verifyAdmin(requestSecret);

        return ResponseEntity.ok(cronService.collectByArtist(name));
    }

    @PostMapping("/internal/cron/collect-artist-id")
    public ResponseEntity<CronBatchService.SaveResult> collectByArtistId(
            @RequestHeader("X-Cron-Secret") String requestSecret,
            @RequestParam long artistId) {
        internalSecrets.verifyAdmin(requestSecret);

        return ResponseEntity.ok(cronService.collectByArtistId(artistId));
    }

    @PostMapping("/internal/cron/enrich-ko")
    public ResponseEntity<Void> processKoEnrichment(
            @RequestHeader("X-Cron-Secret") String requestSecret) throws InterruptedException {
        internalSecrets.verifyCron(requestSecret);

        koEnrichmentService.enrichKo();
        return ResponseEntity.accepted().build();
    }

    /// 신곡 알림. Cloud Scheduler가 매일 한국 09:05(am)·18:05(pm)에 부른다(타임존 Asia/Seoul).
    /// **09:00 이전 금지** — 한국 기기가 발송 시간대 밖이라 전원 스킵되고 재발송이 없다.
    /// 발송까지 요청 안에서 끝낸다(Cloud Run은 요청이 끝나면 CPU를 거둔다).
    @PostMapping("/internal/cron/new-releases")
    public ResponseEntity<NewReleaseService.RunResult> newReleases(
            @RequestHeader("X-Cron-Secret") String requestSecret,
            @RequestParam String slot) throws InterruptedException {
        internalSecrets.verifyCron(requestSecret);

        return ResponseEntity.ok(newReleaseService.run(PushVariant.Slot.parse(slot)));
    }

    /// 주간 추천. Cloud Scheduler가 매주 월요일 한국 09:05(am)·18:05(pm)에 부른다.
    @PostMapping("/internal/cron/weekly-picks")
    public ResponseEntity<WeeklyPickService.RunResult> weeklyPicks(
            @RequestHeader("X-Cron-Secret") String requestSecret,
            @RequestParam String slot) {
        internalSecrets.verifyCron(requestSecret);

        return ResponseEntity.ok(weeklyPickService.run(PushVariant.Slot.parse(slot)));
    }
}
