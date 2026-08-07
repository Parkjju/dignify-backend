package com.rta.dignify.controller;

import com.rta.dignify.dto.PushBroadcast;
import com.rta.dignify.global.security.InternalSecrets;
import com.rta.dignify.service.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// PushService와 같은 조건. 키가 없는 로컬 bootRun에선 이 엔드포인트 자체가 없다.
@ConditionalOnProperty(name = "apns.enabled", havingValue = "true")
@RequiredArgsConstructor
@RestController
public class PushInternalController {
    private final PushService pushService;
    private final InternalSecrets internalSecrets;

    /// 공지 발송. userId가 없으면 전체, 있으면 그 유저에게만. 발송 대수를 돌려준다.
    @PostMapping("/internal/push/broadcast")
    public ResponseEntity<Integer> broadcast(@RequestHeader("X-Cron-Secret") String secret, @Valid @RequestBody PushBroadcast body) {
        internalSecrets.verifyAdmin(secret);
        return ResponseEntity.ok(pushService.broadcast(body.title(), body.body(), body.force(), body.userId(), body.minBuild()));
    }
}
