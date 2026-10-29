package com.rta.dignify.service;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.domain.PushLog;
import com.rta.dignify.domain.PushVariant;
import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.repository.PushLogRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.service.cron.CronBatchService;
import com.rta.dignify.service.cron.KoEnrichmentBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// 좋아요한 아티스트의 신곡 — 알림(`POST /internal/cron/new-releases`)과 피드 앞 표시(`GET /feed/new-releases`).
/// 명세는 Confluence 03. Dev 「개발 참고: 신곡 알림」(13008898).
///
/// 상태 테이블이 없다. "신곡"은 `tracks.created_at`(저장 시각)과 `release_date`로 매번 계산한다.
/// - am: iTunes 조회 → 저장 → ko 보강 → **이번 실행이 저장한 곡**으로 A그룹 발송
/// - pm: iTunes를 다시 부르지 않고 **오늘(한국 날짜) 저장된 곡**으로 B그룹 발송. 오늘 pm 이력이 있으면 건너뛴다
///
/// 저장 후 발송 전에 실패하면 그 곡의 알림은 누락이다(at-most-once). 곡은 남아 피드 앞에는 뜬다.
@RequiredArgsConstructor
@Slf4j
@Service
public class NewReleaseService {
    static final Duration WINDOW = Duration.ofDays(7);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /// lookup 한 번에 넣는 아티스트 수. limit=10이 id당이라 응답은 최대 500곡이다.
    static final int LOOKUP_BATCH = 50;
    /// iTunes는 분당 약 20회다. 묶음 사이에 이만큼 쉰다.
    static final long LOOKUP_PAUSE_MS = 3_000;
    /// 제목 뒤의 버전 표기 — "(Remastered 2011)", "[Deluxe]", " - Live", "(feat. X)".
    /// 이걸 떼고 같으면 같은 곡으로 본다(재발매·앨범 수록·선공개 싱글). 리믹스도 같은 곡으로 친다.
    private static final Pattern VERSION_SUFFIX = Pattern.compile("\\s*(\\(.*?\\)|\\[.*?]|\\s-\\s.*)");

    private final ITunesAPIClient iTunesAPIClient;
    private final CronBatchService cronBatchService;
    private final KoEnrichmentBatchService koEnrichmentBatchService;
    private final TrackRepository trackRepository;
    private final PushLogRepository pushLogRepository;
    private final JdbcTemplate jdbcTemplate;
    /// apns.enabled=false(테스트/로컬)면 빈이 없다. 그때는 수집만 한다.
    private final ObjectProvider<PushService> pushService;
    private final Clock clock;

    public record RunResult(int saved, int newReleases, int users, int sent) {
    }

    public RunResult run(PushVariant.Slot slot) throws InterruptedException {
        Instant now = clock.instant();
        String kind = "new_release_" + slot.name().toLowerCase(Locale.ROOT);
        Instant since;
        int saved = 0;
        if (slot == PushVariant.Slot.AM) {
            since = now;
            saved = collect(now);
        } else {
            since = now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant();
            if (pushLogRepository.existsByKindAndSentAtGreaterThanEqual(kind, since)) {
                log.info("[new-release] 오늘 pm 발송이 이미 있다 — 건너뛴다");
                return new RunResult(0, 0, 0, 0);
            }
        }

        List<Track> releases = newReleasesSince(since, clock.instant());
        Map<Long, List<Track>> byUser = bundle(releases, PushVariant.newReleaseGroup(slot));
        PushService push = pushService.getIfAvailable();
        if (push == null || !push.adEnabled() || byUser.isEmpty()) {
            log.info("[new-release] slot={} saved={} releases={} users={} — 발송 없음(push={}, ad={})",
                    slot, saved, releases.size(), byUser.size(), push != null, push != null && push.adEnabled());
            return new RunResult(saved, releases.size(), byUser.size(), 0);
        }
        PushLog record = push.begin(kind, "new_release", "신곡 알림(유저별 문구)", releases.size() + "곡");
        Map<Long, PushService.Alert> alerts = new HashMap<>();
        byUser.forEach((userId, tracks) -> alerts.put(userId, PushService.newReleaseAlert(tracks)));
        int sent = push.sendEach(record, alerts);
        log.info("[new-release] slot={} saved={} releases={} users={} sent={}", slot, saved, releases.size(), byUser.size(), sent);
        return new RunResult(saved, releases.size(), byUser.size(), sent);
    }

    /// 앱 피드 맨 앞에 붙일 신곡 — 좋아요한 아티스트가 최근 7일 안에 낸 곡 중 안 들은 곡, 아티스트 순.
    /// **알림·광고성 동의와 무관하다.** 앱 안 표시는 법상 "전송"이 아니다.
    public List<Long> feedTrackIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Instant now = clock.instant();
        Set<Long> artists = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT t.artist_id FROM users_hype_tracks uht JOIN tracks t ON t.track_id = uht.track_id "
                        + "WHERE uht.user_id = ? AND t.artist_id IS NOT NULL", Long.class, userId));
        Set<Long> seen = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT track_id FROM listened_tracks WHERE user_id = ? UNION SELECT track_id FROM users_hype_tracks WHERE user_id = ?",
                Long.class, userId, userId));
        return byArtist(newReleasesSince(now.minus(WINDOW), now)).entrySet().stream()
                .filter(e -> artists.contains(e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .map(Track::getId)
                .filter(id -> !seen.contains(id))
                .toList();
    }

    /// 좋아요 아티스트 전원의 최근 곡을 받아 발매 7일 이내만 저장한다. 저장 수를 돌려준다.
    /// 알림 미동의 유저의 아티스트도 받는다 — 곡은 피드 앞 표시에도 쓰인다.
    int collect(Instant now) throws InterruptedException {
        List<Long> artistIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT t.artist_id FROM users_hype_tracks uht JOIN tracks t ON t.track_id = uht.track_id "
                        + "WHERE t.artist_id IS NOT NULL ORDER BY t.artist_id", Long.class);
        int saved = 0;
        for (int i = 0; i < artistIds.size(); i += LOOKUP_BATCH) {
            if (i > 0) Thread.sleep(LOOKUP_PAUSE_MS);
            try {
                List<ItunesItem> recent = iTunesAPIClient.lookup(artistIds.subList(i, Math.min(i + LOOKUP_BATCH, artistIds.size())))
                        .stream().filter(item -> isRecentRelease(item.releaseDate(), now)).toList();
                List<String> fresh = recent.stream().map(item -> String.valueOf(item.trackId()))
                        .filter(id -> !trackRepository.existsByExternalIdAndSource(id, "ITUNES")).distinct().toList();
                if (fresh.isEmpty()) continue;
                saved += cronBatchService.saveItems(recent).saved();
                koEnrichmentBatchService.applyKo(fresh, iTunesAPIClient.lookupKrByTrackIds(fresh));
            } catch (RestClientException e) {
                // 한 묶음이 실패해도 나머지 아티스트의 신곡 알림은 나가야 한다.
                log.warn("[new-release] iTunes 조회 실패 batch={} — 건너뛴다: {}", i / LOOKUP_BATCH, e.getMessage());
            }
        }
        log.info("[new-release] artists={} saved={}", artistIds.size(), saved);
        return saved;
    }

    /// 발매 7일 이내이고 이미 발매된 곡. 선주문(발매일이 미래)은 실제 발매 뒤에 잡힌다.
    static boolean isRecentRelease(String releaseDate, Instant now) {
        if (releaseDate == null) return false;
        try {
            Instant released = Instant.parse(releaseDate);
            return !released.isAfter(now) && !released.isBefore(now.minus(WINDOW));
        } catch (DateTimeException e) {
            return false;
        }
    }

    /// since 이후 저장된 신곡 중 재발매가 아닌 것. 같은 아티스트 + 같은 정규화 제목이 since 전에 있었거나
    /// 이번 안에서 먼저 나왔으면(디럭스·스탠더드 동시 발매) 뺀다.
    List<Track> newReleasesSince(Instant since, Instant now) {
        List<Track> candidates = trackRepository.findNewReleaseCandidates(since, now.minus(WINDOW), now);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> artists = candidates.stream().map(Track::getArtistId).collect(Collectors.toSet());
        Set<String> seen = trackRepository.findByArtistIdInAndCreatedAtBefore(artists, since).stream()
                .map(NewReleaseService::sameSongKey).collect(Collectors.toCollection(HashSet::new));
        return candidates.stream().filter(t -> seen.add(sameSongKey(t))).toList();
    }

    private static String sameSongKey(Track t) {
        return t.getArtistId() + "|" + normalizeTitle(t.getTrackName());
    }

    static String normalizeTitle(String title) {
        return VERSION_SUFFIX.matcher(title).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    /// 아티스트별로 묶되 처음 나온 순서를 지킨다. 알림 페이로드의 trackIds가 이 순서다.
    static Map<Long, List<Track>> byArtist(List<Track> tracks) {
        return tracks.stream().collect(Collectors.groupingBy(Track::getArtistId, LinkedHashMap::new, Collectors.toList()));
    }

    /// 유저당 한 건 — 그 유저가 좋아요한 아티스트의 신곡을 아티스트 순으로 묶는다. 이번 실행의 그룹만.
    /// 동의는 여기서도 거른다(발송 쪽 `PushService`가 다시 거르지만 대상 수를 정직하게 세려고).
    private Map<Long, List<Track>> bundle(List<Track> releases, PushVariant group) {
        if (releases.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Track>> byArtist = byArtist(releases);
        String in = byArtist.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Long, Set<Long>> artistsOf = new HashMap<>();
        jdbcTemplate.query("SELECT DISTINCT uht.user_id, t.artist_id FROM users_hype_tracks uht "
                        + "JOIN tracks t ON t.track_id = uht.track_id JOIN users u ON u.user_id = uht.user_id "
                        + "WHERE u.deleted_at IS NULL AND u.ad_push_consent IS TRUE AND uht.user_id % 2 = ? "
                        + "AND t.artist_id IN (" + in + ")",
                rs -> { artistsOf.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getLong(2)); },
                group.mod());

        Map<Long, List<Track>> byUser = new HashMap<>();
        artistsOf.forEach((userId, artists) -> {
            List<Track> tracks = new ArrayList<>();
            byArtist.forEach((artistId, list) -> { if (artists.contains(artistId)) tracks.addAll(list); });
            byUser.put(userId, tracks);
        });
        return byUser;
    }
}
