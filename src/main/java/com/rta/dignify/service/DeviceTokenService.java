package com.rta.dignify.service;

import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class DeviceTokenService {
    /// URLSession이 붙이는 기본 User-Agent: "dignify/12 CFNetwork/3860.700.1 Darwin/25.6.0".
    /// 앞 숫자가 CFBundleVersion(빌드 번호)다. 자릿수 상한과 뒤따르는 숫자 금지(?!\d)는 같이 있어야 한다 —
    /// 상한만 두면 열한 자리가 와도 앞 아홉 자리만 잘라 엉뚱한 빌드로 읽는다.
    private static final Pattern APP_BUILD = Pattern.compile("dignify/(\\d{1,9})(?!\\d)", Pattern.CASE_INSENSITIVE);

    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(Long userId, String token, String environment, String platform, String timeZone, String userAgent) {
        User user = userRepository.getReferenceById(userId);
        Integer appBuild = parseAppBuild(userAgent);
        UserDeviceToken saved = userDeviceTokenRepository.findByToken(token)
                .map(existing -> {
                    existing.reassign(user, environment, platform, timeZone, appBuild);
                    return existing;
                })
                .orElseGet(() -> userDeviceTokenRepository.save(
                        UserDeviceToken.create(user, token, environment, platform, timeZone, appBuild)));

        evictStaleTokens(userId, saved);
    }

    /// 같은 유저·같은 플랫폼의 **다른** 토큰을 지운다.
    ///
    /// 기기 하나가 재설치·데이터 복원 때마다 새 토큰을 받는데, 서버는 옛 토큰이 죽은 걸
    /// 발송이 한 번 실패해봐야 안다. 그동안 행이 쌓여 어드민의 기기 수·빌드 목록이 부풀고
    /// 발송은 매번 죽은 토큰에 한 번씩 헛돈다. 새 토큰이 들어온 순간이 "그 기기의 옛 토큰은
    /// 이제 죽었다"를 알 수 있는 가장 이른 시점이라 여기서 정리한다.
    ///
    /// 플랫폼별로 가르는 건 아이폰과 안드로이드를 같이 쓰는 유저 때문이다. 대신 같은 플랫폼
    /// 기기를 둘 쓰면 나중에 등록한 쪽만 남는다 — APNs·FCM 모두 기기 식별자를 안 줘서
    /// 토큰만으로는 "같은 기기의 새 토큰"과 "다른 기기"를 구분할 방법이 없다.
    ///
    /// 판정을 `isAndroid()`에 맡기는 이유는 "platform이 null이면 iOS" 규칙이 거기 있어서다.
    /// 쿼리로 다시 쓰면 그 규칙이 두 곳으로 갈라진다. 한 유저의 토큰은 몇 개뿐이라 훑어도 된다.
    private void evictStaleTokens(Long userId, UserDeviceToken saved) {
        for (UserDeviceToken other : userDeviceTokenRepository.findByUserId(userId)) {
            if (!other.getToken().equals(saved.getToken()) && other.isAndroid() == saved.isAndroid()) {
                userDeviceTokenRepository.delete(other);
            }
        }
    }

    /// UA에서 빌드 번호를 뽑는다. 앱이 UA를 갈아끼웠거나 curl 같은 데서 온 요청이면 null.
    static Integer parseAppBuild(String userAgent) {
        if (userAgent == null) return null;
        Matcher m = APP_BUILD.matcher(userAgent);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
