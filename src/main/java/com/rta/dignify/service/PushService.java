package com.rta.dignify.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.rta.dignify.domain.PushLog;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.PushLogRepository;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// apns.enabled=true(프로덕션)일 때만 로드. 테스트/로컬엔 APNs 키가 없어 빈을 안 띄운다.
@ConditionalOnProperty(name = "apns.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@Service
public class PushService {
    /// 발송 허용 시간대(기기 로컬 기준). 밖이면 건너뛴다.
    static final int AWAKE_FROM = 9, AWAKE_UNTIL = 22;
    /// 광고성 타입은 21시에 끊는다. 야간(21~08시) 발송은 별도 야간 동의가 필요한데 그건 받지 않는다(제50조 3항).
    static final int AD_AWAKE_UNTIL = 21;
    /// 광고성 정보로 운영하는 타입(2026-10-28 확정). 동의 필터·`(광고)` 접두어·수신거부 카테고리·21시 컷이
    /// 전부 이 집합 하나로 갈린다. `pick_reaction`은 활동 알림이라 밖이다 — 문구에 홍보를 섞는 순간 안으로 와야 한다.
    static final Set<String> AD_TYPES = Set.of("curation", "new_release", "weekly_pick");
    static final String AD_PREFIX = "(광고) ";
    /// iOS가 「수신거부」 액션을 등록해 둔 `UNNotificationCategory` id.
    static final String AD_CATEGORY = "AD_PUSH";
    /// 발송 이력을 몇 건까지 남길지. 어드민 화면이 이만큼을 보여준다.
    public static final int LOG_KEEP = 100;
    /// 공지 발송이 APNs/FCM 응답을 기다리는 한도. 넘기면 받은 데까지만 기록하고 돌아간다.
    private static final long RESULT_WAIT_SECONDS = 20;

    private final ApnsClient sandboxApns;
    private final ApnsClient productionApns;
    /// FCM은 없을 수 있다(fcm.enabled=false). 없으면 안드로이드 토큰만 건너뛰고 iOS 발송은
    /// 그대로 나간다 — 그래서 필수 의존이 아니라 ObjectProvider다.
    private final ObjectProvider<FirebaseMessaging> fcm;
    private final UserDeviceTokenRepository tokenRepository;
    private final PushLogRepository pushLogRepository;

    @Value("${apns.bundle-id}") String bundleId;
    /// 끄면 광고성 발송을 통째로 멈춘다. 켜면 동의한 유저에게만 간다.
    /// 둘 사이가 없는 이유: 동의 컬럼 기본값이 false라 앱 동의 팝업이 나가기 전에 켜면 대상이 0명이고,
    /// 필터 없이 보내면 동의 없는 광고 전송이다.
    @Value("${push.ad.enabled:false}") boolean adEnabled;
    /// 「수신거부」 액션이 들어간 첫 빌드. 이보다 낮은 기기엔 광고성 알림을 안 보낸다 — 알림에서
    /// 바로 거부할 수 없으면 위반이다. 플랫폼마다 빌드 번호 체계가 달라 둘로 둔다.
    @Value("${push.ad.min-build.ios:2147483647}") int adMinBuildIos;
    @Value("${push.ad.min-build.android:2147483647}") int adMinBuildAndroid;

    /// 알림 문구 한 줄. `key`가 있으면 **기기가** 자기 언어로 렌더하고(APNs `loc-key` =
    /// FCM `title_loc_key`), `text`면 서버가 쓴 원문이 그대로 나간다.
    ///
    /// 문구를 발송 경로에서 떼어낸 이유는 경로가 둘(APNs/FCM)이기 때문이다. 붙여두면
    /// "note가 있으면 본문만 바꾼다", "1명일 때만 이름을 댄다" 같은 분기가 양쪽에 복사되고
    /// 한쪽만 고치는 날이 반드시 온다.
    record Line(String key, String arg, String text) {
        static Line loc(String key) { return new Line(key, null, null); }

        static Line loc(String key, String arg) { return new Line(key, arg, null); }

        static Line raw(String text) { return new Line(null, null, text); }

        /// 인자는 있어야 하나뿐이다. APNs·FCM 둘 다 배열로 받아 형태를 맞춰둔다.
        String[] args() { return arg == null ? new String[0] : new String[]{arg}; }
    }

    /// 알림 한 건 = 제목 + 본문 + 종류. 무엇을 말할지는 여기까지고, 어떻게 보낼지는 렌더러가 맡는다.
    ///
    /// `type`은 앱이 **누른 뒤 어디로 갈지**를 정하는 키다(APNs·FCM 둘 다 커스텀 키로 실린다).
    /// 앱은 못 알아보는 값이면 그냥 앱을 열기만 하므로, 새 종류를 늘려도 구버전이 깨지지 않는다.
    /// 계측(`push_opened{type}`)도 같은 값을 쓴다 — 안 실으면 전량 `unknown`으로 뭉친다.
    ///
    /// `pushId`는 발송 이력(`PushLog`)과 열람 이벤트를 잇는 키다. 구버전 앱은 모르는 키라 무시한다.
    ///
    /// 광고성 타입이면 제목 앞에 `(광고)`를 **여기서** 붙인다. 문구마다 넣으면 언젠가 빠뜨린다.
    /// loc-key 제목엔 붙일 수 없다(기기가 렌더한다) — 광고성 문구는 원문으로만 보낸다.
    ///
    /// `trackIds`는 탭했을 때 피드 앞에 붙일 곡(신곡·주간 추천). 없으면 null.
    record Alert(Line title, Line body, String type, String pushId, List<Long> trackIds) {
        Alert {
            if (isAd(type) && title.text() != null && !title.text().startsWith(AD_PREFIX)) {
                title = Line.raw(AD_PREFIX + title.text());
            }
        }

        Alert(Line title, Line body, String type) { this(title, body, type, null, null); }

        Alert withPushId(String pushId) { return new Alert(title, body, type, pushId, trackIds); }
    }

    static boolean isAd(String type) {
        return AD_TYPES.contains(type);
    }

    public boolean adEnabled() {
        return adEnabled;
    }

    /// 내 픽에 반응이 마일스톤에 닿았음을 알린다(§10.5). 발송 여부 판정은 `PickService`가 한다.
    ///
    /// ⚠️ 문구를 서버에서 만들지 않는 이유 — `LocaleContextHolder`는 **반응을 누른 사람**의
    /// 로케일이지 알림을 받는 픽 주인의 것이 아니다. 한국 유저가 미국 유저 픽에 반응하면
    /// 미국 유저에게 한국어 알림이 간다. loc-key로 보내면 기기가 자기 언어로 렌더한다.
    ///
    /// ⚠️ `broadcast`의 시간대 필터를 여기 걸지 않는다. 그건 전체 발송용이고, 개별 반응 푸시에
    /// 걸면 `time_zone`이 null인 기기가 통째로 잘려 조용히 아무것도 안 간다.
    public void sendPickReaction(Long ownerId, String reactorNickname, long count) {
        Alert draft = pickReactionAlert(reactorNickname, count);
        PushLog record = start("pick_reaction", draft, ownerId, null, false);
        Alert alert = draft.withPushId(record.getPushId());

        List<UserDeviceToken> tokens = tokenRepository.findByUserId(ownerId);
        record.candidates(tokens.size());
        // 유저 요청 안에서 불리므로 응답을 기다리지 않는다. 기록은 마지막 응답이 온 콜백에서 한다.
        // ponytail: 요청이 끝난 뒤라 Cloud Run이 CPU를 줄여 늦게 찍힐 수 있다. 저장 한 번이라 버틴다.
        CompletableFuture.allOf(tokens.stream().map(t -> send(t, alert, record)).toArray(CompletableFuture[]::new))
                .whenComplete((v, err) -> save(record));
    }

    /// 1명일 때만 이름을 댄다. 여럿일 때 한 명을 대는 건 나머지를 지운다.
    ///
    /// **길이가 변하는 문구는 전부 본문으로 보낸다.** iOS 알림의 title은 무조건 한 줄이라
    /// 닉네임이 긴 유저(최대 20자)의 이름이 들어가면 잘린다. body는 배너에서 두 줄이고
    /// 펼치면 전부 보인다. 그래서 title은 인자 없는 고정 문구다.
    ///
    /// 본문에 "보러 가기" 같은 유도 문구는 안 넣는다 — 앱에 아직 이 type을 받는 분기가 없어
    /// 탭해도 그 픽으로 못 간다. iOS가 분기를 넣으면 그때 문구도 같이 바꾼다.
    static Alert pickReactionAlert(String reactorNickname, long count) {
        return count == 1
                ? new Alert(Line.loc("push_pick_reaction_first_title"),
                            Line.loc("push_pick_reaction_first", reactorNickname), "pick_reaction")
                : new Alert(Line.loc("push_pick_reaction_milestone_title"),
                            Line.loc("push_pick_reaction_milestone", String.valueOf(count)), "pick_reaction");
    }

    /// 신곡 알림 문구. **UX Writing 미확정** — 기획 초안(Confluence 13074433 1-3)을 그대로 옮겼다.
    /// 제목에 가수명이 들어가 긴 이름은 잘린다("가변 이름은 본문" 원칙과 충돌, 확정 때 같이 판단).
    /// 아티스트 이름은 한국어 표기 우선. tracks는 아티스트 순으로 정렬돼 와야 한다.
    static Alert newReleaseAlert(List<Track> tracks) {
        List<String> artists = tracks.stream().map(t -> t.displayArtistName(Locale.KOREAN)).distinct().toList();
        List<Long> ids = tracks.stream().map(Track::getId).toList();
        String first = artists.get(0);
        if (artists.size() == 1) {
            return new Alert(Line.raw("좋아하실 " + first + " 신곡 들어볼까요? 🎧"),
                    Line.raw(tracks.size() == 1 ? "신곡과 어울리는 노래들도 함께 준비했어요💗" : first + "의 노래들을 모았어요💪"),
                    "new_release", null, ids);
        }
        return new Alert(Line.raw("좋아하실 아티스트의 신곡들 궁금하지 않으세요?👀"),
                Line.raw(first + ", " + artists.get(1) + " 외에도 여러 가수의 곡들이 기다리고 있어요🥹"),
                "new_release", null, ids);
    }

    /// 주간 추천 문구. 제목은 고정이고 길이가 바뀌는 기준 곡 제목은 본문에 둔다(제목은 한 줄만 보인다).
    static Alert weeklyPickAlert(String seedTitle, List<Long> trackIds) {
        return new Alert(Line.raw(WEEKLY_PICK_TITLE),
                Line.raw("「" + seedTitle + "」를 좋아하셨다면 들어 볼 만한 곡이에요"), "weekly_pick", null, trackIds);
    }

    static final String WEEKLY_PICK_TITLE = "이번 주 추천 5곡";

    /// 운영자가 손으로 쏘는 공지 푸시(run-cron.sh push). 발송 대수를 돌려준다.
    ///
    /// 문구를 loc-key가 아니라 원문 그대로 받는 이유는, 보낼 때마다 내용이 달라 앱에 키를
    /// 미리 심어둘 수가 없어서다. 대신 앱 업데이트 없이 아무 문구나 바로 나간다.
    ///
    /// force=false면 기기 로컬 시각이 새벽인 사람은 건너뛴다. 발송 시각을 사람이 고르는 이상
    /// 한국 낮에 쏘면 미국 유저는 반드시 새벽이라, 막지 않으면 언젠가 한 번은 새벽에 울린다.
    ///
    /// userId를 주면 그 유저 기기에만 보내고 시간대도 안 본다. 본인 기기로 미리 찍어보는
    /// 용도라, 지금이 몇 시든 눌렀을 때 와야 확인이 된다.
    ///
    /// minBuild를 주면 그 빌드 이상인 기기에만 나간다. 신기능 안내를 구버전 유저가 받으면
    /// 눌러도 그 화면이 없어서다. 빌드를 아직 모르는 기기(app_build null)는 구버전으로 치고 뺀다 —
    /// 앱을 한 번 켜야 채워지는 값이라, 확실할 때만 보내는 쪽이 맞다.
    /// type은 앱의 딥링크 분기와 `push_opened` 축을 정한다. `curation`을 넣어야 큐레이션 세트가
    /// 앞으로 나온다 — 안 넣으면 눌러도 일반 피드 첫 화면이라, 알림에서 본 곡을 못 찾는다.
    /// 비워 보내면 `notice`(그냥 앱 열기)로 친다.
    public int broadcast(String title, String body, String type, boolean force, Long userId, Integer minBuild) {
        Alert draft = new Alert(Line.raw(title), Line.raw(body),
                type == null || type.isBlank() ? "notice" : type);
        // 본인 기기 미리보기(userId 지정)는 운영자 자신에게 가는 것이라 동의·플래그를 안 본다.
        boolean ad = userId == null && isAd(draft.type());
        if (ad) requireAdEnabled();
        PushLog record = start(userId == null ? "broadcast" : "test", draft, userId, minBuild, force);
        Alert alert = draft.withPushId(record.getPushId());

        List<UserDeviceToken> targets = userId != null ? tokenRepository.findByUserId(userId)
                : ad ? tokenRepository.findAdConsented()
                : tokenRepository.findAll();

        record.candidates(targets.size());

        List<CompletableFuture<Void>> results = new ArrayList<>();
        for (UserDeviceToken t : targets) {
            if (!meetsMinBuild(t.getAppBuild(), minBuild) || ad && !meetsAdMinBuild(t)) { record.skippedBuild(); continue; }
            if (userId == null && !force && !isAwakeHour(t.getTimeZone(), Instant.now(), ad ? AD_AWAKE_UNTIL : AWAKE_UNTIL)) { record.skippedQuiet(); continue; }
            results.add(send(t, alert, record));
        }
        await(results);
        save(record);
        return results.size();
    }

    /// 유저마다 내용이 다른 광고성 발송(신곡 알림·주간 추천)의 이력 행을 연다. 실행 1회 = 이력 1행 = pushId 1개라
    /// A/B 그룹(실행 시각)별 열람률이 이 행에서 바로 갈린다. 호출부는 pushId를 받아 자기 기록에 남긴 뒤
    /// `sendEach`로 보낸다(주간 추천은 `weekly_picks.push_id`).
    public PushLog begin(String kind, String type, String title, String body) {
        requireAdEnabled();
        return PushLog.start(kind, type, AD_PREFIX + title, body, null, null, false);
    }

    /// 유저별 알림을 그 유저의 모든 기기로. 광고성 규칙(동의·빌드·21시)은 `broadcast`와 같다.
    /// 발송 대수를 돌려준다.
    public int sendEach(PushLog record, Map<Long, Alert> byUser) {
        if (byUser.isEmpty()) return 0;
        List<UserDeviceToken> targets = tokenRepository.findAdConsentedByUserIds(byUser.keySet());
        record.candidates(targets.size());

        List<CompletableFuture<Void>> results = new ArrayList<>();
        for (UserDeviceToken t : targets) {
            if (!meetsAdMinBuild(t)) { record.skippedBuild(); continue; }
            if (!isAwakeHour(t.getTimeZone(), Instant.now(), AD_AWAKE_UNTIL)) { record.skippedQuiet(); continue; }
            results.add(send(t, byUser.get(t.getUser().getId()).withPushId(record.getPushId()), record));
        }
        await(results);
        save(record);
        return results.size();
    }

    private void requireAdEnabled() {
        if (!adEnabled) throw new BusinessException(ErrorCode.AD_PUSH_DISABLED);
    }

    private boolean meetsAdMinBuild(UserDeviceToken t) {
        return meetsMinBuild(t.getAppBuild(), t.isAndroid() ? adMinBuildAndroid : adMinBuildIos);
    }

    /// 응답을 이 요청 안에서 기다린다. 요청이 끝나면 Cloud Run이 CPU를 거둬 콜백이 기어가고,
    /// 어드민이 발송 직후 이력을 열었을 때 결과가 비어 있게 된다.
    private static void await(List<CompletableFuture<Void>> results) {
        try {
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).get(RESULT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("푸시 응답 대기 한도 초과 — 받은 데까지만 기록한다");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // send가 돌려주는 future는 실패로 끝나지 않는다. 혹시 오면 받은 데까지 기록한다.
        }
    }

    private static PushLog start(String kind, Alert alert, Long userId, Integer minBuild, boolean force) {
        return PushLog.start(kind, alert.type(), describe(alert.title()), describe(alert.body()), userId, minBuild, force);
    }

    /// 이력 표시용. loc-key는 기기가 렌더하므로 서버가 아는 건 키와 인자뿐이다.
    static String describe(Line line) {
        if (line.key() == null) return line.text();
        return line.arg() == null ? line.key() : line.key() + "(" + line.arg() + ")";
    }

    /// 발송 기록이 실패해도 발송 자체엔 영향이 없어야 한다 — 이미 나간 뒤다.
    private void save(PushLog record) {
        if (record.pending() == 0) record.completed();
        try {
            pushLogRepository.save(record);
            pushLogRepository.trim(LOG_KEEP);
        } catch (RuntimeException e) {
            log.warn("푸시 발송 기록 저장 실패", e);
        }
    }

    /// 토큰 하나에 발송. 어느 경로로 갈지는 토큰이 안다(`isAndroid`).
    /// 돌려주는 future는 응답(성공·실패 모두)이 record에 기록된 뒤 정상 완료된다.
    private CompletableFuture<Void> send(UserDeviceToken t, Alert alert, PushLog record) {
        record.attempted();
        return t.isAndroid() ? sendFcm(t, alert, record) : sendApns(t, alert, record);
    }

    /// APNs 발송. 만료 토큰이라고 답하면 그 자리에서 지운다.
    private CompletableFuture<Void> sendApns(UserDeviceToken t, Alert alert, PushLog record) {
        ApnsClient client = "production".equals(t.getEnvironment()) ? productionApns : sandboxApns;
        var push = new SimpleApnsPushNotification(t.getToken(), bundleId, apnsPayload(alert));
        return client.sendNotification(push).handle((res, err) -> {
            if (err != null) {
                // 연결 실패 등 APNs가 답을 못 준 경우. 토큰 문제가 아니라 지우지 않는다.
                log.warn("APNs 발송 실패 tokenId={}", t.getId(), err);
                record.failed(t.getId(), t.getUser().getId(), false, err.getClass().getSimpleName(), false);
            } else if (res.isAccepted()) {
                record.accepted(false);
            } else {
                boolean invalid = res.getTokenInvalidationTimestamp().isPresent();
                record.failed(t.getId(), t.getUser().getId(), false, res.getRejectionReason().orElse("unknown"), invalid);
                if (invalid) {
                    tokenRepository.deleteById(t.getId());   // deleteById 자체가 트랜잭션
                }
            }
            return null;
        });
    }

    /// FCM 발송. APNs와 마찬가지로 **논블로킹이어야 한다** — 반응 푸시는 유저 요청을 처리하는
    /// 중에 불리므로, 여기서 응답을 기다리면 반응을 누를 때마다 그만큼 느려진다.
    ///
    /// UNREGISTERED는 앱이 지워졌거나 토큰이 회전된 경우로, APNs의 만료 토큰과 같은 자리라 지운다.
    /// 그 외 실패(일시적 네트워크·쿼터)는 두고 다음 발송에서 다시 시도한다.
    private CompletableFuture<Void> sendFcm(UserDeviceToken t, Alert alert, PushLog record) {
        FirebaseMessaging messaging = fcm.getIfAvailable();
        if (messaging == null) {
            // 여기서 조용히 빠지면 "보냈다고 나오는데 안 온다"가 된다. broadcast는 이걸 발송
            // 대수에 세고 있어서 호출부에서도 구별이 안 간다 — 그래서 이력엔 실패로 남긴다.
            log.warn("FCM 미설정(fcm.enabled=false)이라 안드로이드 기기 {}를 건너뛴다", t.getId());
            record.failed(t.getId(), t.getUser().getId(), true, "FCM_DISABLED", false);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> done = new CompletableFuture<>();
        ApiFutures.addCallback(messaging.sendAsync(fcmMessage(t.getToken(), alert)),
                new ApiFutureCallback<String>() {
                    @Override public void onSuccess(String messageId) {
                        log.info("FCM 발송 성공 tokenId={} messageId={}", t.getId(), messageId);
                        record.accepted(true);
                        done.complete(null);
                    }

                    @Override public void onFailure(Throwable err) {
                        MessagingErrorCode code = err instanceof FirebaseMessagingException e
                                ? e.getMessagingErrorCode() : null;
                        // 남은 것 전부를 남긴다. FCM 실패는 유저 요청 밖(콜백)에서 나므로
                        // 여기서 안 찍으면 어디에도 흔적이 없다.
                        log.warn("FCM 발송 실패 tokenId={} code={}", t.getId(), code, err);
                        boolean invalid = code == MessagingErrorCode.UNREGISTERED;
                        record.failed(t.getId(), t.getUser().getId(), true,
                                code != null ? code.name() : err.getClass().getSimpleName(), invalid);
                        // 삭제보다 먼저 끝낸다. 삭제가 터지면 broadcast가 대기 한도까지 묶인다.
                        done.complete(null);
                        if (invalid) {
                            tokenRepository.deleteById(t.getId());
                        }
                    }
                }, Runnable::run);
        return done;
    }

    static String apnsPayload(Alert alert) {
        ApnsPayloadBuilder builder = new SimpleApnsPayloadBuilder()
                .setSound(SimpleApnsPayloadBuilder.DEFAULT_SOUND_FILENAME)
                .addCustomProperty("type", alert.type());
        if (alert.pushId() != null) {
            builder.addCustomProperty("push_id", alert.pushId());
        }
        if (alert.trackIds() != null) {
            builder.addCustomProperty("trackIds", alert.trackIds());
        }
        if (isAd(alert.type())) {
            builder.setCategoryName(AD_CATEGORY);
        }

        if (alert.title().key() != null) {
            builder.setLocalizedAlertTitle(alert.title().key(), alert.title().args());
        } else {
            builder.setAlertTitle(alert.title().text());
        }
        if (alert.body().key() != null) {
            builder.setLocalizedAlertMessage(alert.body().key(), alert.body().args());
        } else {
            builder.setAlertBody(alert.body().text());
        }
        return builder.build();
    }

    /// 문구를 `android.notification`에만 싣는다. 최상위 `notification`은 제목·본문을 문자열로
    /// 요구해서 loc-key를 실을 자리가 없다 — 안드로이드 전용 블록에 넣어야 기기가 strings.xml에서
    /// 자기 언어로 꺼내 쓴다(그래서 키 이름이 리소스 이름과 같아야 한다).
    ///
    /// 광고성 타입은 예외로 **data만** 보낸다. `android.notification`이 있으면 백그라운드에선 시스템이 알림을
    /// 직접 그리고 앱 코드가 한 줄도 안 돌아서 「수신거부」 액션을 달 수 없다. 앱(`DignifyMessagingService`)이
    /// `title`·`body`로 알림을 만든다. 광고성 문구는 원문뿐이라(Alert 주석) loc-key를 실을 일이 없다.
    /// 우선순위를 HIGH로 둬야 도즈 중인 기기에서도 바로 깨어 알림을 띄운다.
    static Message fcmMessage(String token, Alert alert) {
        if (isAd(alert.type()) && alert.title().text() != null && alert.body().text() != null) {
            Message.Builder message = Message.builder()
                    .setToken(token)
                    .putData("type", alert.type())
                    .putData("title", alert.title().text())
                    .putData("body", alert.body().text())
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());
            if (alert.pushId() != null) message.putData("push_id", alert.pushId());
            if (alert.trackIds() != null) {
                message.putData("trackIds", alert.trackIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
            }
            return message.build();
        }
        AndroidNotification.Builder notification = AndroidNotification.builder().setSound("default");

        if (alert.title().key() != null) {
            notification.setTitleLocalizationKey(alert.title().key());
            for (String arg : alert.title().args()) notification.addTitleLocalizationArg(arg);
        } else {
            notification.setTitle(alert.title().text());
        }
        if (alert.body().key() != null) {
            notification.setBodyLocalizationKey(alert.body().key());
            for (String arg : alert.body().args()) notification.addBodyLocalizationArg(arg);
        } else {
            notification.setBody(alert.body().text());
        }

        Message.Builder message = Message.builder();
        if (alert.pushId() != null) {
            message.putData("push_id", alert.pushId());
        }
        // FCM data는 문자열만 받는다.
        if (alert.trackIds() != null) {
            message.putData("trackIds", alert.trackIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        }
        return message
                .setToken(token)
                .putData("type", alert.type())
                .setAndroidConfig(AndroidConfig.builder().setNotification(notification.build()).build())
                .build();
    }

    /// minBuild가 없으면 전부 통과. 있으면 빌드를 아는 기기만, 그중 기준 이상만 통과한다.
    static boolean meetsMinBuild(Integer appBuild, Integer minBuild) {
        if (minBuild == null) return true;
        return appBuild != null && appBuild >= minBuild;
    }

    /// 타임존은 앱이 보낸 문자열이라 그대로 믿을 수 없다. 비었거나 파싱이 안 되면 UTC로 친다 —
    /// 구버전 앱 토큰이 여기 걸리는데, 한 명 때문에 발송 전체가 죽는 것보다 낫다.
    static boolean isAwakeHour(String timeZone, Instant now) {
        return isAwakeHour(timeZone, now, AWAKE_UNTIL);
    }

    static boolean isAwakeHour(String timeZone, Instant now, int until) {
        ZoneId zone = ZoneOffset.UTC;
        if (timeZone != null && !timeZone.isBlank()) {
            try {
                zone = ZoneId.of(timeZone);
            } catch (DateTimeException ignored) {
                // UTC 유지
            }
        }
        int hour = now.atZone(zone).getHour();
        return hour >= AWAKE_FROM && hour < until;
    }
}
