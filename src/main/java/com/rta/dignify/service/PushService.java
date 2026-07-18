package com.rta.dignify.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// apns.enabled=true(프로덕션)일 때만 로드. 테스트/로컬엔 APNs 키가 없어 빈을 안 띄운다.
@ConditionalOnProperty(name = "apns.enabled", havingValue = "true")
@RequiredArgsConstructor
@Service
public class PushService {
    private final ApnsClient sandboxApns;
    private final ApnsClient productionApns;
    private final UserDeviceTokenRepository tokenRepository;

    @Value("${apns.bundle-id}") String bundleId;

    /// 요청한 아티스트가 추가됐음을 알린다. 문구는 loc-key로 보내고 앱이 기기 언어로 렌더한다.
    public void sendArtistAdded(Long userId, String artistName) {
        String payload = new SimpleApnsPayloadBuilder()
                .setLocalizedAlertTitle("push_artist_added_title", artistName)  // title-loc-key + args(%@=아티스트명)
                .setLocalizedAlertMessage("push_artist_added_body")             // loc-key
                .setSound(SimpleApnsPayloadBuilder.DEFAULT_SOUND_FILENAME)
                .build();

        for (UserDeviceToken t : tokenRepository.findByUserId(userId)) {
            ApnsClient client = "production".equals(t.getEnvironment()) ? productionApns : sandboxApns;
            var push = new SimpleApnsPushNotification(t.getToken(), bundleId, payload);
            client.sendNotification(push).whenComplete((res, err) -> {
                if (err == null && !res.isAccepted() && res.getTokenInvalidationTimestamp().isPresent()) {
                    tokenRepository.deleteById(t.getId());   // deleteById 자체가 트랜잭션
                }
            });
        }
    }
}
