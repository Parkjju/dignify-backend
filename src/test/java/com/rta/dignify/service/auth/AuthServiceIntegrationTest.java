package com.rta.dignify.service.auth;

import com.rta.dignify.client.apple.AppleAuthClient;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.dto.auth.AuthTokenResponse;
import com.rta.dignify.global.jwt.JwtProvider;
import com.rta.dignify.repository.UserRepository;
import com.rta.dignify.repository.UserTokenRepository;
import com.rta.dignify.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class AuthServiceIntegrationTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private AppleAuthClient appleAuthClient;

    @Test
    @DisplayName("애플 로그인 테스트")
    @Transactional
    void signInWithAppleTest() {
        String testToken = "test-token";

        // Apple identity token 검증 시나리오는 통과처리
        given(appleAuthClient.verifyIdentityToken(testToken)).willReturn(new AppleIdentity("test@gmail.com", "test-apple-id"));
        given(jwtProvider.generateAccessToken(any()))
                .willReturn("test-access-token1")
                .willReturn("test-access-token2")
                .willReturn("test-access-token3");
        given(jwtProvider.generateRefreshToken(any()))
                .willReturn("test-refresh-token1")
                .willReturn("test-refresh-token2")
                .willReturn("test-refresh-token3");
        given(jwtProvider.getAccessTokenExpiration()).willReturn(3600000L);
        given(jwtProvider.getRefreshTokenExpiration()).willReturn(2592000000L);

        // 신규 유저 테스트
        AuthTokenResponse newUserResponse = authService.signInWithApple(testToken);
        assertThat(newUserResponse.accessToken()).isNotNull();
        assertThat(newUserResponse.refreshToken()).isNotNull();
        assertThat(newUserResponse.accessTokenExpiresAt()).isNotNull();

        // 기존 유저 테스트: 재로그인시 리프레시 및 액세스 토큰 재발급
        AuthTokenResponse oldUserResponse = authService.signInWithApple(testToken);
        assertThat(newUserResponse.refreshToken()).isNotEqualTo(oldUserResponse.refreshToken());
        assertThat(newUserResponse.accessToken()).isNotEqualTo(oldUserResponse.accessToken());

        // 유저 soft delete 후 재로그인 테스트
        // 재로그인 전 발급된 토큰 엔티티 2개인지 테스트
        assertThat(userTokenRepository.findAll().size()).isEqualTo(2);

        // 유저 soft delete
        User deletingUser = userRepository.findAll().getFirst();
        deletingUser.deleteUser();

        // 재로그인시 soft delete 데이터 hard delete
        // cascade 정책으로 USER 연관데이터 전체 삭제
        // TODO: UserAuth 및 다른 엔티티 삭제 여부 체크 필요
        AuthTokenResponse reLoginUser = authService.signInWithApple(testToken);
        assertThat(userTokenRepository.findAll().size()).isEqualTo(1);
        assertThat(reLoginUser.refreshToken()).isNotNull();
        assertThat(reLoginUser.accessToken()).isNotNull();
    }
}
