package com.rta.dignify.controller;

import com.rta.dignify.repository.PushLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    @PostMapping("/push/{pushId}/opened")
    public ResponseEntity<Void> opened(@PathVariable String pushId) {
        pushLogRepository.markOpened(pushId);
        return ResponseEntity.noContent().build();
    }
}
