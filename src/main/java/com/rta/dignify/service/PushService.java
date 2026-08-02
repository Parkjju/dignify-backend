package com.rta.dignify.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
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
@Service
public class PushService {
    /// 발송 허용 시간대(기기 로컬 기준). 밖이면 건너뛴다.
    static final int AWAKE_FROM = 9, AWAKE_UNTIL = 22;

    private final ApnsClient sandboxApns;
    private final ApnsClient productionApns;
    private final UserDeviceTokenRepository tokenRepository;

    @Value("${apns.bundle-id}") String bundleId;

    /// 요청한 아티스트가 추가됐음을 알린다. 문구는 loc-key로 보내고 앱이 기기 언어로 렌더한다.
    ///
    /// note를 주면 본문만 그 문구로 바뀐다("일부 앨범만 올라왔어요" 같은 안내). 번역은 안 되니
    /// 보내는 사람이 유저 언어를 보고 써야 한다. 제목은 그대로 loc-key라 기기 언어로 나온다.
    public void sendArtistAdded(Long userId, String artistName, String note) {
        String payload = artistAddedPayload(artistName, note);

        for (UserDeviceToken t : tokenRepository.findByUserId(userId)) {
            send(t, payload);
        }
    }

    static String artistAddedPayload(String artistName, String note) {
        ApnsPayloadBuilder builder = new SimpleApnsPayloadBuilder()
                .setLocalizedAlertTitle("push_artist_added_title", artistName)  // title-loc-key + args(%@=아티스트명)
                .setSound(SimpleApnsPayloadBuilder.DEFAULT_SOUND_FILENAME);
        if (note == null || note.isBlank()) {
            builder.setLocalizedAlertMessage("push_artist_added_body");         // loc-key
        } else {
            builder.setAlertBody(note);
        }
        return builder.build();
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
        String payload = new SimpleApnsPayloadBuilder()
                .setAlertTitle(title)
                .setAlertBody(body)
                .setSound(SimpleApnsPayloadBuilder.DEFAULT_SOUND_FILENAME)
                .build();

        List<UserDeviceToken> targets = userId == null
                ? tokenRepository.findAll()
                : tokenRepository.findByUserId(userId);

        int sent = 0;
        for (UserDeviceToken t : targets) {
            if (!meetsMinBuild(t.getAppBuild(), minBuild)) continue;
            if (userId == null && !force && !isAwakeHour(t.getTimeZone(), Instant.now())) continue;
            send(t, payload);
            sent++;
        }
        return sent;
    }

    /// 토큰 하나에 발송. APNs가 만료 토큰이라고 답하면 그 자리에서 지운다.
    private void send(UserDeviceToken t, String payload) {
        ApnsClient client = "production".equals(t.getEnvironment()) ? productionApns : sandboxApns;
        var push = new SimpleApnsPushNotification(t.getToken(), bundleId, payload);
        client.sendNotification(push).whenComplete((res, err) -> {
            if (err == null && !res.isAccepted() && res.getTokenInvalidationTimestamp().isPresent()) {
                tokenRepository.deleteById(t.getId());   // deleteById 자체가 트랜잭션
            }
        });
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
