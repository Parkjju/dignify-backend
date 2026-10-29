package com.rta.dignify.controller;

import com.rta.dignify.dto.push.PushOptOutRequest;
import com.rta.dignify.dto.user.AdPushConsentResponse;
import com.rta.dignify.repository.PushLogRepository;
import com.rta.dignify.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class PushController {
    private final PushLogRepository pushLogRepository;
    private final UserService userService;

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

    /// 알림의 「수신거부」 액션. `/opened`와 같은 이유로 인증이 없고 앱은 Authorization 헤더를 붙이지 않는다.
    /// 여기에 로그인을 요구하면 수신거부 방해다(제50조 5항). 회차별 거부 수를 보려고 그 발송 이력에도 센다.
    @PostMapping("/push/{pushId}/opt-out")
    public AdPushConsentResponse optOut(@PathVariable String pushId, @RequestBody @Valid PushOptOutRequest request) {
        AdPushConsentResponse response = userService.optOutByDeviceToken(request.deviceToken());
        pushLogRepository.markOptedOut(pushId);
        return response;
    }
}
