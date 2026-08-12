package com.rta.dignify.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/// fcm.enabled=true일 때만 로드. APNs와 스위치를 따로 두는 이유는 두 플랫폼 출시가
/// 같은 날이 아니어서다 — 서비스 계정 키가 아직 없는 환경에서도 iOS 푸시는 그대로 나가야 한다.
/// 꺼져 있으면 `PushService`가 안드로이드 토큰만 건너뛴다.
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
@Configuration
public class FcmConfig {
    /// 서비스 계정 JSON 원문. 파일 경로가 아니라 내용을 통째로 받는다 — Cloud Run엔
    /// 붙일 디스크가 없어서 APNs 키(.p8)도 같은 방식으로 넣고 있다.
    @Value("${fcm.service-account-json}") String serviceAccountJson;

    @Bean
    FirebaseMessaging firebaseMessaging() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));

        FirebaseApp app = FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build());

        return FirebaseMessaging.getInstance(app);
    }
}
