package com.rta.dignify.controller;

import com.rta.dignify.dto.admin.ArtistIdBatch;
import com.rta.dignify.dto.admin.ArtistRequestItem;
import com.rta.dignify.dto.admin.GenreStat;
import com.rta.dignify.dto.admin.KoBatch;
import com.rta.dignify.dto.admin.PushTargets;
import com.rta.dignify.dto.admin.SeedPickCreate;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.global.security.InternalSecrets;
import com.rta.dignify.service.AdminService;
import com.rta.dignify.service.SeedPickService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/// 어드민 화면용. 화면은 /internal/admin.html에 있고, 발송/요청처리는 기존
/// /internal/push/broadcast, /internal/artist-requests/{id}/resolve를 그대로 쓴다.
@RequiredArgsConstructor
@RequestMapping("/internal/admin")
@RestController
public class AdminController {

    private final AdminService adminService;
    private final SeedPickService seedPickService;
    private final InternalSecrets internalSecrets;

    @GetMapping("/curation")
    public List<FeedItem> getCurationSet(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.getCurationSet();
    }

    /// 본문의 목록이 곧 세트다(전체 교체). 빈 배열이면 세트가 비워진다.
    @PutMapping("/curation")
    public List<FeedItem> replaceCurationSet(@RequestHeader("X-Cron-Secret") String secret, @RequestBody List<Long> trackIds) {
        internalSecrets.verifyAdmin(secret);
        return adminService.replaceCurationSet(trackIds);
    }

    @GetMapping("/artist-requests")
    public List<ArtistRequestItem> getPendingArtistRequests(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.getPendingArtistRequests();
    }

    @GetMapping("/itunes/artists")
    public List<ItunesItem> searchItunesArtists(@RequestHeader("X-Cron-Secret") String secret, @RequestParam String q) {
        internalSecrets.verifyAdmin(secret);
        return adminService.searchItunesArtists(q);
    }

    @GetMapping("/genre-stats")
    public List<GenreStat> getGenreStats(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.getGenreStats();
    }

    @GetMapping("/ko-pending")
    public long getKoPendingCount(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.getKoPendingCount();
    }

    /// 한 배치만 처리하고 남은 수를 돌려준다. 큐를 비우려면 화면이 remaining이 0이 될 때까지 반복한다.
    @PostMapping("/enrich-ko/batch")
    public KoBatch enrichKoBatch(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.enrichKoBatch();
    }

    @GetMapping("/artist-id-pending")
    public long getArtistIdPendingCount(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.getArtistIdPendingCount();
    }

    /// after는 화면이 들고 다니는 커서다. 처음엔 0, 다음부터는 직전 응답의 cursor를 그대로 넘긴다.
    /// checked가 0이면 커서가 끝까지 간 것이다.
    @PostMapping("/backfill-artist-id/batch")
    public ArtistIdBatch backfillArtistIdBatch(@RequestHeader("X-Cron-Secret") String secret,
                                               @RequestParam(defaultValue = "0") long after) {
        internalSecrets.verifyAdmin(secret);
        return adminService.backfillArtistIdBatch(after);
    }

    @GetMapping("/push/users")
    public PushTargets getPushTargets(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.getPushTargets();
    }

    @GetMapping("/seed-picks")
    public Map<String, Object> getSeedPickStatus(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return seedPickService.status();
    }

    @PostMapping("/seed-picks")
    public Map<String, Object> createSeedPick(@RequestHeader("X-Cron-Secret") String secret, @RequestBody SeedPickCreate request) {
        internalSecrets.verifyAdmin(secret);
        return seedPickService.create(request);
    }

    /// 실제로 붙은 수를 돌려준다. 시드 계정이 모자라면 count보다 작다.
    @PostMapping("/seed-picks/{pickId}/react")
    public int reactSeedPick(@RequestHeader("X-Cron-Secret") String secret, @PathVariable Long pickId, @RequestParam int count) {
        internalSecrets.verifyAdmin(secret);
        return seedPickService.react(pickId, count);
    }

    /// 픽 곡마다 운영 계정 per명이 하입한다. 새로 들어간 하입 수를 돌려준다.
    @PostMapping("/seed-picks/{pickId}/hype")
    public int hypeSeedPick(@RequestHeader("X-Cron-Secret") String secret, @PathVariable Long pickId, @RequestParam int per) {
        internalSecrets.verifyAdmin(secret);
        return seedPickService.hype(pickId, per);
    }

    /// 픽 없이 운영 계정만 만든다. 실제로 만든 수를 돌려준다.
    @PostMapping("/seed-accounts")
    public int createSeedAccounts(@RequestHeader("X-Cron-Secret") String secret, @RequestParam int count) {
        internalSecrets.verifyAdmin(secret);
        return seedPickService.createAccounts(count);
    }

    /// 앱에 보이는 재생 수에 n을 얹는다(실제 재생 play_count는 그대로). 얹은 뒤 보이는 값을 돌려준다.
    @PostMapping("/seed-picks/{pickId}/plays")
    public int addSeedPlays(@RequestHeader("X-Cron-Secret") String secret, @PathVariable Long pickId, @RequestParam int n) {
        internalSecrets.verifyAdmin(secret);
        return seedPickService.addPlays(pickId, n);
    }
}
