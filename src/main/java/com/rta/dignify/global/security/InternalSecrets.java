package com.rta.dignify.global.security;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/// /internal/* 를 여는 시크릿. 값을 둘로 나눈 이유는 유출 반경 때문이다.
///
/// - 어드민(admin.secret): 사람이 브라우저에서 부르는 경로. 화면이 localStorage에 상시 보관하므로
///   가장 새기 쉽다. 새더라도 대량 수집 배치까지 열리지는 않게 한다.
/// - 크론(cron.secret): 몇 시간짜리 배치. 스크립트만 부르고 브라우저에는 들어가지 않는다.
///
/// 헤더 이름은 양쪽 다 X-Cron-Secret 하나를 쓴다. 경로마다 맞는 값이 다를 뿐이다.
@Slf4j
@Component
public class InternalSecrets {
    private final String cronSecret;
    private final String adminSecret;

    public InternalSecrets(@Value("${cron.secret}") String cronSecret, @Value("${admin.secret}") String adminSecret) {
        this.cronSecret = cronSecret;
        this.adminSecret = adminSecret;
        if (cronSecret.equals(adminSecret)) {
            log.warn("ADMIN_SECRET이 없어 CRON_SECRET을 그대로 쓴다 — 어드민 화면 시크릿이 새면 수집 배치까지 열린다.");
        }
    }

    public void verifyAdmin(String secret) {
        verify(adminSecret, secret);
    }

    public void verifyCron(String secret) {
        verify(cronSecret, secret);
    }

    private void verify(String expected, String given) {
        if (!expected.equals(given)) {
            throw new BusinessException(ErrorCode.CRON_SECRET_INVALID);
        }
    }
}
