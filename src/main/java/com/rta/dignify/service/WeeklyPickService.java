package com.rta.dignify.service;

import com.rta.dignify.domain.PushLog;
import com.rta.dignify.domain.PushVariant;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.WeeklyPick;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserRepository;
import com.rta.dignify.repository.WeeklyPickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// 주간 추천 — 매주 월요일, 좋아요한 곡 하나를 기준으로 무드가 가까운 5곡을 알린다.
/// 명세는 Confluence 03. Dev 「개발 참고: 주간 추천 푸시」(12681240).
///
/// 각 실행은 자기 그룹만 처리하고 그 안에서 계산·발송을 끝낸다(그룹 간에 넘길 상태가 없다).
/// 이번 주 앱 사용 여부로 분기하지 않는다. 평소 피드의 진행 위치(`feed_state`)는 건드리지 않는다.
@RequiredArgsConstructor
@Slf4j
@Service
public class WeeklyPickService {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MIN_HYPES = 4;
    static final int PICKS = 5;
    static final int PER_ARTIST = 2;
    /// 아티스트당 2곡으로 자르고도 5곡이 남게 넉넉히 뽑는다.
    static final int RANKED = 50;

    private final WeeklyPickRepository weeklyPickRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final MoodRecommender moodRecommender;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<PushService> pushService;
    private final Clock clock;

    public record RunResult(int targets, int picked, int sent) {
    }

    public RunResult run(PushVariant.Slot slot) {
        PushService push = pushService.getIfAvailable();
        if (push == null) {
            // 발송 없이 기록만 남기면 그 주를 소진한다. 푸시가 없는 환경에선 아예 돌지 않는다.
            throw new BusinessException(ErrorCode.AD_PUSH_DISABLED);
        }
        PushLog record = push.begin("weekly_pick", "weekly_pick", PushService.WEEKLY_PICK_TITLE, "「기준 곡」(유저별)");
        LocalDate weekStart = LocalDate.now(clock.withZone(KST)).with(DayOfWeek.MONDAY);
        List<Long> targets = targets(PushVariant.weeklyPickGroup(slot));

        Map<Long, PushService.Alert> alerts = new HashMap<>();
        for (Long userId : targets) {
            pick(userId, weekStart, record.getPushId()).ifPresent(p -> alerts.put(userId,
                    PushService.weeklyPickAlert(p.getSeedTrack().displayTrackName(Locale.KOREAN), p.getTrackIds())));
        }
        int sent = push.sendEach(record, alerts);
        log.info("[weekly-pick] slot={} week={} targets={} picked={} sent={}", slot, weekStart, targets.size(), alerts.size(), sent);
        return new RunResult(targets.size(), alerts.size(), sent);
    }

    /// 좋아요 4곡 이상 + 광고성 동의 + 이 그룹. 3곡 이하는 대기(다음 발송일에 다시 본다).
    List<Long> targets(PushVariant group) {
        return jdbcTemplate.queryForList("SELECT u.user_id FROM users u JOIN users_hype_tracks uht ON uht.user_id = u.user_id "
                + "WHERE u.deleted_at IS NULL AND u.ad_push_consent IS TRUE AND u.user_id % 2 = ? "
                + "GROUP BY u.user_id HAVING count(*) >= " + MIN_HYPES + " ORDER BY u.user_id", Long.class, group.mod());
    }

    /// 이 유저의 이번 주 추천을 정해 **발송 전에** 저장한다. 이미 이번 주 것이 있거나 기준 곡 후보가 없으면 빈 값.
    Optional<WeeklyPick> pick(Long userId, LocalDate weekStart, String pushId) {
        if (weeklyPickRepository.existsByUserIdAndWeekStart(userId, weekStart)) {
            return Optional.empty();
        }
        Optional<Long> seed = seedCandidate(userId);
        if (seed.isEmpty()) {
            return Optional.empty();   // 후보 소진 — 이력을 지우지 않고 새 좋아요를 기다린다
        }
        List<Long> ranked = moodRecommender.similarTo(List.of(seed.get()), userId, RANKED);
        Map<Long, Track> tracks = trackRepository.findAllById(ranked).stream()
                .collect(Collectors.toMap(Track::getId, Function.identity()));
        List<Long> picks = capPerArtist(ranked, id -> artistKey(tracks.get(id)), PER_ARTIST, PICKS);
        if (picks.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(weeklyPickRepository.saveAndFlush(WeeklyPick.create(
                    userRepository.getReferenceById(userId), trackRepository.findById(seed.get()).orElseThrow(), picks, weekStart, pushId)));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();   // 같은 주에 이미 들어갔다(동시 실행). 중복보다 누락
        }
    }

    /// 벡터 있는 좋아요 곡 중 무작위 1곡. 빼는 것: 이미 기준 곡으로 쓴 곡, 추천 기준으로 고정한 곡,
    /// 고정이 없으면 평소 피드의 시드인 최근 3곡(`MoodRecommender.findSeeds`와 같은 기준) — 평소 피드와 같은 추천이 안 나오게.
    Optional<Long> seedCandidate(Long userId) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT uht.track_id FROM users_hype_tracks uht "
                + "JOIN track_vectors v ON v.track_id = uht.track_id "
                + "WHERE uht.user_id = ? AND uht.is_seed IS FALSE "
                + "AND NOT EXISTS (SELECT 1 FROM weekly_picks w WHERE w.user_id = uht.user_id AND w.seed_track_id = uht.track_id) "
                + "AND (EXISTS (SELECT 1 FROM users_hype_tracks p WHERE p.user_id = uht.user_id AND p.is_seed IS TRUE) "
                + "  OR uht.track_id NOT IN (SELECT r.track_id FROM users_hype_tracks r JOIN track_vectors rv ON rv.track_id = r.track_id "
                + "    WHERE r.user_id = uht.user_id ORDER BY r.user_hype_track_id DESC LIMIT " + MoodRecommender.SEEDS + ")) "
                + "ORDER BY random() LIMIT 1", Long.class, userId);
        return ids.stream().findFirst();
    }

    /// 유사도 순서를 지킨 채 아티스트당 perArtist곡까지, total곡을 채운다. 하한선은 없다.
    static List<Long> capPerArtist(List<Long> ranked, Function<Long, String> artistOf, int perArtist, int total) {
        Map<String, Integer> counts = new HashMap<>();
        List<Long> picks = new ArrayList<>();
        for (Long id : ranked) {
            String artist = artistOf.apply(id);
            if (artist == null) continue;
            if (counts.merge(artist, 1, Integer::sum) > perArtist) continue;
            picks.add(id);
            if (picks.size() == total) break;
        }
        return picks;
    }

    /// artist_id가 없는 옛 곡은 이름으로 묶는다. 곡이 내려가 조회가 안 되면 null(건너뜀).
    private static String artistKey(Track t) {
        if (t == null) return null;
        return t.getArtistId() != null ? "id:" + t.getArtistId() : "name:" + t.getArtistName();
    }
}
