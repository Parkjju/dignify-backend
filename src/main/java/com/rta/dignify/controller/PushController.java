package com.rta.dignify.controller;

import com.rta.dignify.repository.PushLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class PushController {
    private final PushLogRepository pushLogRepository;

    /// 앱이 알림을 눌렀을 때 부른다. 어드민 발송 이력의 열람 수가 된다.
    ///
    /// 인증이 없다. push_id는 추측할 수 없는 UUID라 막을 이유가 그 발송의 숫자가 부푸는 정도뿐이고,
    /// 앱은 **Authorization 헤더를 붙이면 안 된다** — 만료 토큰이면 permitAll 경로에서도 필터가 401을 낸다.
    /// 없는 push_id여도 204다. 알려줄 게 없고, 앱은 결과를 안 본다.
    ///
    /// OS는 User-Agent로 가른다. iOS는 URLSession 기본 UA("dignify/24 CFNetwork/… Darwin/…"),
    /// 안드로이드는 앱이 직접 붙이는 "dignify/13"이라 CFNetwork 유무로 갈린다. 둘 다 아니면 총계에만 센다.
    @PostMapping("/push/{pushId}/opened")
    public ResponseEntity<Void> opened(@PathVariable String pushId,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase();
        boolean app = ua.startsWith("dignify/");
        boolean ios = app && ua.contains("cfnetwork");
        pushLogRepository.markOpened(pushId, ios ? 1 : 0, app && !ios ? 1 : 0);
        return ResponseEntity.noContent().build();
    }
}
