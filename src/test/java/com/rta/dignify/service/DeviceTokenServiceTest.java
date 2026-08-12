package com.rta.dignify.service;

import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserDeviceToken;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class DeviceTokenServiceTest {
    /// 실제로 들어오는 UA 형태 (Cloud Run 로그 기준). 앞 숫자가 CFBundleVersion.
    static final String UA_12 = "dignify/12 CFNetwork/3860.700.1 Darwin/25.6.0";

    @Autowired
    DeviceTokenService deviceTokenService;

    @Autowired
    UserDeviceTokenRepository userDeviceTokenRepository;

    @Autowired
    UserRepository userRepository;

    User user;

    @Test
    @DisplayName("신규 토큰 등록 — row가 생성된다")
    void registerNewToken() {
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", "ios", "Asia/Seoul", UA_12);

        assertThat(userDeviceTokenRepository.findAll()).hasSize(1);
        UserDeviceToken saved = userDeviceTokenRepository.findByToken("token-abc").orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getEnvironment()).isEqualTo("sandbox");
        assertThat(saved.getTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(saved.getAppBuild()).isEqualTo(12);
    }

    @Test
    @DisplayName("UA에서 빌드 번호를 뽑는다 — 못 읽으면 null")
    void parsesAppBuildFromUserAgent() {
        assertThat(DeviceTokenService.parseAppBuild(UA_12)).isEqualTo(12);
        assertThat(DeviceTokenService.parseAppBuild("Dignify/7 CFNetwork/1.0 Darwin/1.0")).isEqualTo(7);
        assertThat(DeviceTokenService.parseAppBuild(null)).isNull();
        assertThat(DeviceTokenService.parseAppBuild("curl/8.7.1")).isNull();
        // 자릿수가 말이 안 되면 Integer로 못 담는다. 터지느니 모르는 걸로 친다.
        assertThat(DeviceTokenService.parseAppBuild("dignify/99999999999 CFNetwork/1.0")).isNull();
    }

    @Test
    @DisplayName("UA를 못 읽은 재등록 — 기존 빌드를 지우지 않는다")
    void reRegisterWithoutUserAgentKeepsBuild() {
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", "ios", "Asia/Seoul", UA_12);
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", "ios", "Asia/Seoul", null);

        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().getAppBuild()).isEqualTo(12);
    }

    @Test
    @DisplayName("같은 토큰 재등록 — 중복 없이 environment만 갱신된다")
    void reRegisterUpdatesInPlace() {
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", "ios", "Asia/Seoul", UA_12);
        deviceTokenService.register(user.getId(), "token-abc", "production", "ios", "America/Phoenix", UA_12);

        assertThat(userDeviceTokenRepository.findAll()).hasSize(1);   // 새 row 안 생김
        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().getEnvironment())
                .isEqualTo("production");
        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().getTimeZone())
                .isEqualTo("America/Phoenix");
    }

    @Test
    @DisplayName("구버전 앱이 타임존 없이 재등록 — 기존 타임존을 지우지 않는다")
    void reRegisterWithoutTimeZoneKeepsIt() {
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", "ios", "Asia/Seoul", UA_12);
        deviceTokenService.register(user.getId(), "token-abc", "production", "ios", null, null);

        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().getTimeZone())
                .isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("같은 토큰이 다른 유저로 등록 — 소유자가 이전된다(토큰 unique)")
    void reassignToAnotherUser() {
        User other = userRepository.save(User.create("other@gmail.com", "other"));
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", "ios", "Asia/Seoul", UA_12);
        deviceTokenService.register(other.getId(), "token-abc", "sandbox", "ios", "Asia/Seoul", UA_12);

        assertThat(userDeviceTokenRepository.findAll()).hasSize(1);
        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().getUser().getId())
                .isEqualTo(other.getId());
    }

    @Test
    @DisplayName("platform으로 발송 경로가 갈린다 — 안 보내면(배포된 iOS 앱) iOS로 친다")
    void platformDecidesRoute() {
        deviceTokenService.register(user.getId(), "token-ios", "sandbox", null, "Asia/Seoul", UA_12);
        deviceTokenService.register(user.getId(), "token-and", "sandbox", "android", "Asia/Seoul", null);

        assertThat(userDeviceTokenRepository.findByToken("token-ios").orElseThrow().isAndroid()).isFalse();
        assertThat(userDeviceTokenRepository.findByToken("token-and").orElseThrow().isAndroid()).isTrue();
    }

    @Test
    @DisplayName("platform 없이 재등록 — 기존 platform을 지우지 않는다(안드로이드가 iOS로 안 돌아간다)")
    void reRegisterWithoutPlatformKeepsIt() {
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", "android", "Asia/Seoul", null);
        deviceTokenService.register(user.getId(), "token-abc", "sandbox", null, "Asia/Seoul", null);

        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().isAndroid()).isTrue();
    }

    @Test
    @DisplayName("같은 기기가 새 토큰으로 등록 — 옛 토큰은 그 자리에서 지워진다")
    void newTokenEvictsOldOneOnSamePlatform() {
        deviceTokenService.register(user.getId(), "token-old", "production", "android", "Asia/Seoul", null);
        deviceTokenService.register(user.getId(), "token-new", "production", "android", "Asia/Seoul", null);

        assertThat(userDeviceTokenRepository.findByUserId(user.getId()))
                .extracting(UserDeviceToken::getToken)
                .containsExactly("token-new");
    }

    @Test
    @DisplayName("아이폰과 안드로이드를 같이 쓰면 둘 다 남는다 — 플랫폼이 다르면 안 지운다")
    void keepsTokensOnOtherPlatform() {
        deviceTokenService.register(user.getId(), "token-ios", "production", null, "Asia/Seoul", UA_12);
        deviceTokenService.register(user.getId(), "token-and", "production", "android", "Asia/Seoul", null);

        assertThat(userDeviceTokenRepository.findByUserId(user.getId()))
                .extracting(UserDeviceToken::getToken)
                .containsExactlyInAnyOrder("token-ios", "token-and");
    }

    @Test
    @DisplayName("남의 토큰은 안 건드린다 — 정리는 그 유저 범위 안에서만")
    void doesNotEvictAnotherUsersToken() {
        User other = userRepository.save(User.create("other@gmail.com", "other"));
        deviceTokenService.register(other.getId(), "token-other", "production", "android", "Asia/Seoul", null);
        deviceTokenService.register(user.getId(), "token-mine", "production", "android", "Asia/Seoul", null);

        assertThat(userDeviceTokenRepository.findByToken("token-other")).isPresent();
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("test@gmail.com", "nickname"));
    }
}
