package com.rta.dignify.config;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

// apns.enabled=true(프로덕션)일 때만 로드. 테스트엔 키가 없어 클라이언트 생성이 실패하므로 제외.
@ConditionalOnProperty(name = "apns.enabled", havingValue = "true")
@Configuration
public class ApnsConfig {
    @Value("${apns.key-p8}") String keyP8;
    @Value("${apns.key-id}") String keyId;
    @Value("${apns.team-id}") String teamId;

    @Bean(destroyMethod = "close")
    ApnsClient sandboxApns() throws Exception {
        return build(ApnsClientBuilder.DEVELOPMENT_APNS_HOST);
    }

    @Bean(destroyMethod = "close")
    ApnsClient productionApns() throws Exception {
        return build(ApnsClientBuilder.PRODUCTION_APNS_HOST);
    }

    private ApnsClient build(String host) throws Exception {
        ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(
                new ByteArrayInputStream(keyP8.getBytes(StandardCharsets.UTF_8)), teamId, keyId);

        return new ApnsClientBuilder().setApnsServer(host).setSigningKey(signingKey).build();
    }
}
