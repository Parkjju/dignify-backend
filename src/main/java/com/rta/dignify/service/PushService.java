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
import com.rta.dignify.domain.UserDeviceToken;
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
import java.util.List;

// apns.enabled=true(프로덕션)일 때만 로드. 테스트/로컬엔 APNs 키가 없어 빈을 안 띄운다.
@ConditionalOnProperty(name = "apns.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@Service
public class PushService {
    /// 발송 허용 시간대(기기 로컬 기준). 밖이면 건너뛴다.
    static final int AWAKE_FROM = 9, AWAKE_UNTIL = 22;

    private final ApnsClient sandboxApns;
    private final ApnsClient productionApns;
    /// FCM은 없을 수 있다(fcm.enabled=false). 없으면 안드로이드 토큰만 건너뛰고 iOS 발송은
    /// 그대로 나간다 — 그래서 필수 의존이 아니라 ObjectProvider다.
    private final ObjectProvider<FirebaseMessaging> fcm;
    private final UserDeviceTokenRepository tokenRepository;

    @Value("${apns.bundle-id}") String bundleId;

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

    /// 알림 한 건 = 제목 + 본문. 무엇을 말할지는 여기까지고, 어떻게 보낼지는 렌더러가 맡는다.
    record Alert(Line title, Line body) { }

    /// 요청한 아티스트가 추가됐음을 알린다. 문구는 loc-key로 보내고 앱이 기기 언어로 렌더한다.
    ///
    /// note를 주면 본문만 그 문구로 바뀐다("일부 앨범만 올라왔어요" 같은 안내). 번역은 안 되니
    /// 보내는 사람이 유저 언어를 보고 써야 한다. 제목은 그대로 loc-key라 기기 언어로 나온다.
    public void sendArtistAdded(Long userId, String artistName, String note) {
        Alert alert = artistAddedAlert(artistName, note);

        for (UserDeviceToken t : tokenRepository.findByUserId(userId)) {
            send(t, alert);
        }
    }

    static Alert artistAddedAlert(String artistName, String note) {
        Line body = (note == null || note.isBlank())
                ? Line.loc("push_artist_added_body")
                : Line.raw(note);
        return new Alert(Line.loc("push_artist_added_title", artistName), body);   // %@ = 아티스트명
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
        Alert alert = pickReactionAlert(reactorNickname, count);

        for (UserDeviceToken t : tokenRepository.findByUserId(ownerId)) {
            send(t, alert);
        }
    }

    /// 1명일 때만 이름을 댄다. 여럿일 때 한 명을 대는 건 나머지를 지운다.
    ///
    /// **길이가 변하는 문구는 전부 본문으로 보낸다.** iOS 알림의 title은 무조건 한 줄이라
    /// 닉네임이 긴 유저(최대 20자)의 이름이 들어가면 잘린다. body는 배너에서 두 줄이고
    /// 펼치면 전부 보인다. 그래서 title은 인자 없는 고정 문구다.
    ///
    /// 본문에 "보러 가기" 같은 유도 문구는 안 넣는다 — 딥링크가 없어 탭해도 그 픽으로 못 간다.
    static Alert pickReactionAlert(String reactorNickname, long count) {
        return count == 1
                ? new Alert(Line.loc("push_pick_reaction_first_title"),
                            Line.loc("push_pick_reaction_first", reactorNickname))
                : new Alert(Line.loc("push_pick_reaction_milestone_title"),
                            Line.loc("push_pick_reaction_milestone", String.valueOf(count)));
    }

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
    public int broadcast(String title, String body, boolean force, Long userId, Integer minBuild) {
        Alert alert = new Alert(Line.raw(title), Line.raw(body));

        List<UserDeviceToken> targets = userId == null
                ? tokenRepository.findAll()
                : tokenRepository.findByUserId(userId);

        int sent = 0;
        for (UserDeviceToken t : targets) {
            if (!meetsMinBuild(t.getAppBuild(), minBuild)) continue;
            if (userId == null && !force && !isAwakeHour(t.getTimeZone(), Instant.now())) continue;
            send(t, alert);
            sent++;
        }
        return sent;
    }

    /// 토큰 하나에 발송. 어느 경로로 갈지는 토큰이 안다(`isAndroid`).
    private void send(UserDeviceToken t, Alert alert) {
        if (t.isAndroid()) {
            sendFcm(t, alert);
        } else {
            sendApns(t, alert);
        }
    }

    /// APNs 발송. 만료 토큰이라고 답하면 그 자리에서 지운다.
    private void sendApns(UserDeviceToken t, Alert alert) {
        ApnsClient client = "production".equals(t.getEnvironment()) ? productionApns : sandboxApns;
        var push = new SimpleApnsPushNotification(t.getToken(), bundleId, apnsPayload(alert));
        client.sendNotification(push).whenComplete((res, err) -> {
            if (err == null && !res.isAccepted() && res.getTokenInvalidationTimestamp().isPresent()) {
                tokenRepository.deleteById(t.getId());   // deleteById 자체가 트랜잭션
            }
        });
    }

    /// FCM 발송. APNs와 마찬가지로 **논블로킹이어야 한다** — 반응 푸시는 유저 요청을 처리하는
    /// 중에 불리므로, 여기서 응답을 기다리면 반응을 누를 때마다 그만큼 느려진다.
    ///
    /// UNREGISTERED는 앱이 지워졌거나 토큰이 회전된 경우로, APNs의 만료 토큰과 같은 자리라 지운다.
    /// 그 외 실패(일시적 네트워크·쿼터)는 두고 다음 발송에서 다시 시도한다.
    private void sendFcm(UserDeviceToken t, Alert alert) {
        FirebaseMessaging messaging = fcm.getIfAvailable();
        if (messaging == null) {
            // 여기서 조용히 빠지면 "보냈다고 나오는데 안 온다"가 된다. broadcast는 이걸 발송
            // 대수에 세고 있어서 호출부에서도 구별이 안 간다.
            log.warn("FCM 미설정(fcm.enabled=false)이라 안드로이드 기기 {}를 건너뛴다", t.getId());
            return;
        }

        ApiFutures.addCallback(messaging.sendAsync(fcmMessage(t.getToken(), alert)),
                new ApiFutureCallback<String>() {
                    @Override public void onSuccess(String messageId) {
                        log.info("FCM 발송 성공 tokenId={} messageId={}", t.getId(), messageId);
                    }

                    @Override public void onFailure(Throwable err) {
                        MessagingErrorCode code = err instanceof FirebaseMessagingException e
                                ? e.getMessagingErrorCode() : null;
                        // 남은 것 전부를 남긴다. FCM 실패는 유저 요청 밖(콜백)에서 나므로
                        // 여기서 안 찍으면 어디에도 흔적이 없다.
                        log.warn("FCM 발송 실패 tokenId={} code={}", t.getId(), code, err);
                        if (code == MessagingErrorCode.UNREGISTERED) {
                            tokenRepository.deleteById(t.getId());
                        }
                    }
                }, Runnable::run);
    }

    static String apnsPayload(Alert alert) {
        ApnsPayloadBuilder builder = new SimpleApnsPayloadBuilder()
                .setSound(SimpleApnsPayloadBuilder.DEFAULT_SOUND_FILENAME);

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
    static Message fcmMessage(String token, Alert alert) {
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

        return Message.builder()
                .setToken(token)
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
        ZoneId zone = ZoneOffset.UTC;
        if (timeZone != null && !timeZone.isBlank()) {
            try {
                zone = ZoneId.of(timeZone);
            } catch (DateTimeException ignored) {
                // UTC 유지
            }
        }
        int hour = now.atZone(zone).getHour();
        return hour >= AWAKE_FROM && hour < AWAKE_UNTIL;
    }
}
