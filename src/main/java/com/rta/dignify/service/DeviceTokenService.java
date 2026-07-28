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
    public void register(Long userId, String token, String environment, String timeZone, String userAgent) {
        User user = userRepository.getReferenceById(userId);
        Integer appBuild = parseAppBuild(userAgent);
        userDeviceTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.reassign(user, environment, timeZone, appBuild),
                () -> userDeviceTokenRepository.save(UserDeviceToken.create(user, token, environment, timeZone, appBuild))
        );
    }

    /// UA에서 빌드 번호를 뽑는다. 앱이 UA를 갈아끼웠거나 curl 같은 데서 온 요청이면 null.
    static Integer parseAppBuild(String userAgent) {
        if (userAgent == null) return null;
        Matcher m = APP_BUILD.matcher(userAgent);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
