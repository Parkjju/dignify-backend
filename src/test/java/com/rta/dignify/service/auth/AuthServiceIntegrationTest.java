package com.rta.dignify.service.auth;

import com.rta.dignify.client.apple.AppleAuthClient;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.dto.auth.AuthTokenResponse;
import com.rta.dignify.global.jwt.JwtProvider;
import com.rta.dignify.repository.UserAuthRepository;
import com.rta.dignify.repository.UserRepository;
import com.rta.dignify.repository.UserTokenRepository;
import com.rta.dignify.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class AuthServiceIntegrationTest {
    @InjectMocks
    private AuthService authService;

    @Mock
    private UserAuthRepository userAuthRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTokenRepository userTokenRepository;
    @Mock
    private JwtProvider jwtProvider;
    @MockitoBean
    private AppleAuthClient appleAuthClient;

    @Test
    @DisplayName("애플 로그인 테스트")
    void signInWithAppleTest() {
        // Apple identity token 검증 시나리오는 통과처리
        given(appleAuthClient.verifyIdentityToken("test token")).willReturn(new AppleIdentity("test@gmail.com", "test-apple-id"));
        given(jwtProvider.generateAccessToken(any())).willReturn("test-access-token");
        given(jwtProvider.generateRefreshToken(any())).willReturn("test-refresh-token");

        // 신규 유저 테스트
        AuthTokenResponse newUserResponse = authService.signInWithApple("test token");
        assertThat(newUserResponse.accessToken()).isNotNull();
        assertThat(newUserResponse.refreshToken()).isNotNull();
        assertThat(newUserResponse.accessTokenExpiresAt()).isNotNull();

        // 기존 유저 테스트
        AuthTokenResponse oldUserResponse = authService.signInWithApple("test token");

    }
}
