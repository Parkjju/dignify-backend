package com.rta.dignify.controller;

import com.rta.dignify.dto.admin.ArtistRequestItem;
import com.rta.dignify.dto.admin.GenreStat;
import com.rta.dignify.dto.admin.KoBatch;
import com.rta.dignify.dto.admin.PushTargets;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.global.security.InternalSecrets;
import com.rta.dignify.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/// 어드민 화면용. 화면은 /internal/admin.html에 있고, 발송/요청처리는 기존
/// /internal/push/broadcast, /internal/artist-requests/{id}/resolve를 그대로 쓴다.
@RequiredArgsConstructor
@RequestMapping("/internal/admin")
@RestController
public class AdminController {

    private final AdminService adminService;
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

    @GetMapping("/push/users")
    public PushTargets getPushTargets(@RequestHeader("X-Cron-Secret") String secret) {
        internalSecrets.verifyAdmin(secret);
        return adminService.getPushTargets();
    }
}
