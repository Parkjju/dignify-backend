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
        deviceTokenService.register(user.getId(), "token-abc", "sandbox");

        assertThat(userDeviceTokenRepository.findAll()).hasSize(1);
        UserDeviceToken saved = userDeviceTokenRepository.findByToken("token-abc").orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getEnvironment()).isEqualTo("sandbox");
    }

    @Test
    @DisplayName("같은 토큰 재등록 — 중복 없이 environment만 갱신된다")
    void reRegisterUpdatesInPlace() {
        deviceTokenService.register(user.getId(), "token-abc", "sandbox");
        deviceTokenService.register(user.getId(), "token-abc", "production");

        assertThat(userDeviceTokenRepository.findAll()).hasSize(1);   // 새 row 안 생김
        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().getEnvironment())
                .isEqualTo("production");
    }

    @Test
    @DisplayName("같은 토큰이 다른 유저로 등록 — 소유자가 이전된다(토큰 unique)")
    void reassignToAnotherUser() {
        User other = userRepository.save(User.create("other@gmail.com", "other"));
        deviceTokenService.register(user.getId(), "token-abc", "sandbox");
        deviceTokenService.register(other.getId(), "token-abc", "sandbox");

        assertThat(userDeviceTokenRepository.findAll()).hasSize(1);
        assertThat(userDeviceTokenRepository.findByToken("token-abc").orElseThrow().getUser().getId())
                .isEqualTo(other.getId());
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("test@gmail.com", "nickname"));
    }
}
