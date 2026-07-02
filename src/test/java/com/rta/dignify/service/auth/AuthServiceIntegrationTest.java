package com.rta.dignify.service.auth;

import com.rta.dignify.client.apple.AppleAuthClient;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserToken;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.dto.auth.AuthTokenResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.global.jwt.JwtProvider;
import com.rta.dignify.global.util.TokenHasher;
import com.rta.dignify.repository.UserRepository;
import com.rta.dignify.repository.UserTokenRepository;
import com.rta.dignify.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("탈퇴 후 재로그인 - Apple이 email을 안 주면 기존 유저 email 재사용")
    @Transactional
    void reSignUpReusesEmailWhenTokenHasNoEmail() {
        String testToken = "test-token";
        String appleId = "test-apple-id";
        String originalEmail = "reuse@gmail.com";

        // 1회차(신규가입)는 email 있음, 2회차(재로그인)는 Apple이 email을 안 줌(null).
        // Apple은 identity token의 email 클레임을 "최초 인증" 때만 넣어주므로 재로그인 토큰엔 email이 없다.
        given(appleAuthClient.verifyIdentityToken(testToken))
                .willReturn(new AppleIdentity(originalEmail, appleId))
                .willReturn(new AppleIdentity(null, appleId));
        given(jwtProvider.generateAccessToken(any())).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(any()))
                .willReturn("refresh-token1")
                .willReturn("refresh-token2");
        given(jwtProvider.getAccessTokenExpiration()).willReturn(3600000L);
        given(jwtProvider.getRefreshTokenExpiration()).willReturn(2592000000L);

        // 신규 가입 → email 저장 확인
        authService.signInWithApple(testToken);
        User created = userRepository.findAll().getFirst();
        assertThat(created.getEmail()).isEqualTo(originalEmail);

        // soft delete
        created.deleteUser();

        // 재로그인: email이 null인 토큰으로도 NOT NULL 위반 없이 재가입되고, 기존 email을 재사용해야 함
        AuthTokenResponse reLogin = authService.signInWithApple(testToken);
        assertThat(reLogin.accessToken()).isNotNull();
        assertThat(reLogin.refreshToken()).isNotNull();

        // 기존 유저는 hard delete되고 새 유저가 같은 email로 생성됨
        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().getEmail()).isEqualTo(originalEmail);
        assertThat(users.getFirst().getDeletedAt()).isNull();
    }

    // 1. testToken으로 refreshToken 호출 - DB에 매칭되는 UserToken 없어 AUTH_TOKEN_INVALID 발생
    // 2. hashedRefreshTestToken1로 UserToken 엔티티 생성 및 저장 (DB 조회 성공 케이스 준비)
    // 3. testToken으로 refreshToken 재호출
    //  - TokenHasher.hash(testToken) -> hashedRefreshTestToken1로 기존 row 조회 성공
    //  - jwtProvider로 새 access/refresh 토큰 발급
    //  - TokenHasher.hash(generatedRefreshToken) -> hashedRefreshTestToken2
    //  - rotate()로 기존 row 갱신 (새 row 생성 아님)
    // 4. row 개수 1개 유지 + 마지막 row의 hash가 hashedRefreshTestToken2인지 검증
    @Test
    @DisplayName("리프레시 토큰 테스트")
    @Transactional
    void refreshTokenTest() {
        String testToken = "test-token";
        String hashedRefreshTestToken1 = "test-hashed-token1";
        String hashedRefreshTestToken2 = "test-hashed-token2";
        String generatedRefreshToken = "test-generated-refresh-token";
        String generatedAccessToken = "test-generated-access-token";

        given(jwtProvider.generateRefreshToken(any())).willReturn(generatedRefreshToken);
        given(jwtProvider.generateAccessToken(any())).willReturn(generatedAccessToken);
        given(jwtProvider.getAccessTokenExpiration()).willReturn(3600000L);
        given(jwtProvider.getRefreshTokenExpiration()).willReturn(2592000000L);

        try (MockedStatic<TokenHasher> mocked = Mockito.mockStatic(TokenHasher.class)) {
            mocked.when(() -> TokenHasher.hash(testToken)).thenReturn(hashedRefreshTestToken1);
            mocked.when(() -> TokenHasher.hash(generatedRefreshToken)).thenReturn(hashedRefreshTestToken2);

            // 1. DB 조회 실패 케이스
            assertThatThrownBy(() -> authService.refreshToken(testToken))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_TOKEN_INVALID);

            // 2. 로그인 후 토큰 발급받은 상태 - DB 조회 성공
            User user = User.create("test@gmail.com", "nickname");
            userRepository.save(user);

            UserToken userToken = UserToken.create(user, hashedRefreshTestToken1, Instant.now().plusMillis(10000));
            userTokenRepository.save(userToken);

            AuthTokenResponse dbQuerySuccessCase = authService.refreshToken(testToken);
            assertThat(dbQuerySuccessCase.accessToken()).isEqualTo(generatedAccessToken);
            assertThat(dbQuerySuccessCase.refreshToken()).isEqualTo(generatedRefreshToken);
            assertThat(userTokenRepository.findAll().size()).isEqualTo(1);
            assertThat(userTokenRepository.findAll().getLast().getRefreshTokenHash()).isEqualTo(hashedRefreshTestToken2);
        }
    }

    @Test
    @DisplayName("로그아웃 테스트")
    @Transactional
    void logoutTest() {
        String testRefreshToken = "test-refresh-token";
        String hashedTestRefreshToken = "test-hashed-refresh-token";
        try (MockedStatic<TokenHasher> mocked = Mockito.mockStatic(TokenHasher.class)) {
            mocked.when(() -> TokenHasher.hash(testRefreshToken)).thenReturn(hashedTestRefreshToken);

            User user = User.create("test@gmail.com", "test-nickname");
            userRepository.save(user);

            UserToken userToken = UserToken.create(user, hashedTestRefreshToken, Instant.now());
            userTokenRepository.save(userToken);

            // 1. 유저토큰 저장
            assertThat(userTokenRepository.findAll().size()).isEqualTo(1);

            // 2. 로그아웃 후 유저토큰 제거 검증
            authService.logout(testRefreshToken);
            assertThat(userTokenRepository.findAll().isEmpty()).isTrue();
        }
    }

    @Test
    @DisplayName("회원탈퇴 테스트")
    @Transactional
    void withdrawTest() {
        String testRefreshToken = "test-refresh-token";
        String hashedTestRefreshToken1 = "test-hashed-refresh-token1";
        String hashedTestRefreshToken2 = "test-hashed-refresh-token2";
        try (MockedStatic<TokenHasher> mocked = Mockito.mockStatic(TokenHasher.class)) {
            mocked.when(() -> TokenHasher.hash(testRefreshToken)).thenReturn(hashedTestRefreshToken1);

            // 1. DB 조회 실패 케이스
            assertThatThrownBy(() -> authService.withdraw(testRefreshToken))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_TOKEN_INVALID);

            User user = User.create("test@gmail.com", "test-nickname");
            userRepository.save(user);

            // 멀티 디바이스 로그인 상태
            UserToken userToken1 = UserToken.create(user, hashedTestRefreshToken1, Instant.now());
            UserToken userToken2 = UserToken.create(user, hashedTestRefreshToken2, Instant.now());
            userTokenRepository.saveAll(List.of(userToken1, userToken2));

            assertThat(userTokenRepository.findAll().size()).isEqualTo(2);

            // 2. 회원탈퇴
            authService.withdraw(testRefreshToken);

            // 3. 멀티디바이스 토큰 전체 삭제 검증
            assertThat(userTokenRepository.findAll().isEmpty()).isTrue();

            // 4. 유저 SOFT DELETE 상태 검증
            assertThat(userRepository.findAll().getFirst().getDeletedAt()).isNotNull();
        }
    }
}
