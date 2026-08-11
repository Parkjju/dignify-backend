package com.rta.dignify.service.auth;

import com.rta.dignify.client.apple.AppleAuthClient;
import com.rta.dignify.client.google.GoogleAuthClient;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserAuth;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.dto.auth.AuthTokenResponse;
import com.rta.dignify.dto.auth.GoogleIdentity;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.global.jwt.JwtProvider;
import com.rta.dignify.repository.UserAuthRepository;
import com.rta.dignify.repository.UserRepository;
import com.rta.dignify.repository.UserTokenRepository;
import com.rta.dignify.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 구글 로그인. 애플과 겹치는 부분(토큰 발급·재로그인)은 AuthServiceIntegrationTest가 이미 덮으므로
 * 여기선 구글에만 있는 판단 셋을 본다 — <b>이메일로 기존 계정 연결</b>, email_verified 거절,
 * 탈퇴 계정 이메일 재사용.
 */
@SpringBootTest
class GoogleSignInIntegrationTest {

    private static final String GOOGLE_TOKEN = "google-id-token";
    private static final String APPLE_TOKEN = "apple-identity-token";
    private static final String EMAIL = "digger@gmail.com";
    private static final String GOOGLE_ID = "google-sub-1";

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAuthRepository userAuthRepository;
    @Autowired
    private UserTokenRepository userTokenRepository;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private GoogleAuthClient googleAuthClient;
    @MockitoBean
    private AppleAuthClient appleAuthClient;

    @BeforeEach
    void setUp() {
        // refresh_token_hash가 unique라 호출마다 다른 값이 나와야 한다. 고정값을 주면
        // 두 번째 로그인에서 제약 위반이 나 테스트가 로직이 아니라 픽스처 때문에 깨진다.
        AtomicInteger seq = new AtomicInteger();
        given(jwtProvider.generateAccessToken(any()))
                .willAnswer(invocation -> "access-token-" + seq.incrementAndGet());
        given(jwtProvider.generateRefreshToken(any()))
                .willAnswer(invocation -> "refresh-token-" + seq.incrementAndGet());
        given(jwtProvider.getAccessTokenExpiration()).willReturn(3600000L);
        given(jwtProvider.getRefreshTokenExpiration()).willReturn(2592000000L);
    }

    private void givenGoogleToken(String email, String googleId, boolean emailVerified) {
        given(googleAuthClient.verifyIdToken(GOOGLE_TOKEN))
                .willReturn(new GoogleIdentity(email, googleId, emailVerified));
    }

    @Test
    @DisplayName("신규 구글 유저는 가입 처리된다")
    @Transactional
    void newGoogleUserSignsUp() {
        givenGoogleToken(EMAIL, GOOGLE_ID, true);

        AuthTokenResponse response = authService.signInWithGoogle(GOOGLE_TOKEN);

        assertThat(response.accessToken()).isNotNull();
        assertThat(response.refreshToken()).isNotNull();

        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().getEmail()).isEqualTo(EMAIL);

        List<UserAuth> auths = userAuthRepository.findAll();
        assertThat(auths).hasSize(1);
        assertThat(auths.getFirst().getProvider()).isEqualTo("GOOGLE");
        assertThat(auths.getFirst().getProviderUserId()).isEqualTo(GOOGLE_ID);
    }

    @Test
    @DisplayName("기존 구글 유저 재로그인 - 유저는 안 늘고 토큰만 재발급")
    @Transactional
    void existingGoogleUserSignsIn() {
        givenGoogleToken(EMAIL, GOOGLE_ID, true);

        AuthTokenResponse first = authService.signInWithGoogle(GOOGLE_TOKEN);
        AuthTokenResponse second = authService.signInWithGoogle(GOOGLE_TOKEN);

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(userAuthRepository.findAll()).hasSize(1);
        assertThat(userTokenRepository.findAll()).hasSize(2);   // 멀티 디바이스처럼 토큰만 2건
    }

    @Test
    @DisplayName("같은 이메일의 애플 계정이 있으면 새 계정이 아니라 그 계정에 연결된다")
    @Transactional
    void googleLinksToExistingAppleAccountBySameEmail() {
        // iOS에서 애플로 가입한 상태
        given(appleAuthClient.verifyIdentityToken(APPLE_TOKEN))
                .willReturn(new AppleIdentity(EMAIL, "apple-sub-1"));
        authService.signInWithApple(APPLE_TOKEN);

        Long appleUserId = userRepository.findAll().getFirst().getId();

        // 같은 사람이 안드로이드에서 구글로 로그인
        givenGoogleToken(EMAIL, GOOGLE_ID, true);
        authService.signInWithGoogle(GOOGLE_TOKEN);

        // 유저는 한 명 그대로, 인증수단만 두 개
        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().getId()).isEqualTo(appleUserId);

        List<UserAuth> auths = userAuthRepository.findAll();
        assertThat(auths).hasSize(2);
        assertThat(auths).extracting(UserAuth::getProvider).containsExactlyInAnyOrder("APPLE", "GOOGLE");
        assertThat(auths).allSatisfy(auth -> assertThat(auth.getUser().getId()).isEqualTo(appleUserId));

        // 연결된 계정으로 다시 구글 로그인해도 계정이 늘지 않는다(이제 provider 조회로 잡힌다)
        authService.signInWithGoogle(GOOGLE_TOKEN);
        assertThat(userRepository.findAll()).hasSize(1);
        assertThat(userAuthRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("애플 이메일 가리기 계정과는 이메일이 달라 별도 계정이 된다")
    @Transactional
    void privateRelayAppleAccountStaysSeparate() {
        given(appleAuthClient.verifyIdentityToken(APPLE_TOKEN))
                .willReturn(new AppleIdentity("abc123@privaterelay.appleid.com", "apple-sub-1"));
        authService.signInWithApple(APPLE_TOKEN);

        givenGoogleToken(EMAIL, GOOGLE_ID, true);
        authService.signInWithGoogle(GOOGLE_TOKEN);

        // 같은 사람이지만 이메일이 안 겹쳐 연결 근거가 없다. 자동으로는 못 푸는 케이스라 이게 정상 동작.
        assertThat(userRepository.findAll()).hasSize(2);
        assertThat(userAuthRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("email_verified가 false면 로그인을 거절한다 - 계정 탈취 경로가 된다")
    @Transactional
    void unverifiedEmailIsRejected() {
        givenGoogleToken(EMAIL, GOOGLE_ID, false);

        assertThatThrownBy(() -> authService.signInWithGoogle(GOOGLE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_EMAIL_NOT_VERIFIED);

        assertThat(userRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("email 클레임이 비면 거절한다")
    @Transactional
    void blankEmailIsRejected() {
        givenGoogleToken(null, GOOGLE_ID, true);

        assertThatThrownBy(() -> authService.signInWithGoogle(GOOGLE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_EMAIL_NOT_VERIFIED);

        assertThat(userRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 유저가 구글로 재로그인하면 기존 데이터를 지우고 재가입한다")
    @Transactional
    void withdrawnUserReSignsUp() {
        givenGoogleToken(EMAIL, GOOGLE_ID, true);
        authService.signInWithGoogle(GOOGLE_TOKEN);

        User user = userRepository.findAll().getFirst();
        Long oldId = user.getId();
        user.deleteUser();

        authService.signInWithGoogle(GOOGLE_TOKEN);

        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().getId()).isNotEqualTo(oldId);
        assertThat(users.getFirst().getDeletedAt()).isNull();
        assertThat(users.getFirst().getEmail()).isEqualTo(EMAIL);
        assertThat(userAuthRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("탈퇴한 애플 계정의 이메일로 구글 가입하면 그 계정을 되살리지 않고 새로 만든다")
    @Transactional
    void withdrawnAccountEmailIsNotLinked() {
        given(appleAuthClient.verifyIdentityToken(APPLE_TOKEN))
                .willReturn(new AppleIdentity(EMAIL, "apple-sub-1"));
        authService.signInWithApple(APPLE_TOKEN);
        userRepository.findAll().getFirst().deleteUser();

        givenGoogleToken(EMAIL, GOOGLE_ID, true);
        authService.signInWithGoogle(GOOGLE_TOKEN);

        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().getDeletedAt()).isNull();

        // 탈퇴 계정의 애플 인증수단까지 cascade로 사라지고 구글 것만 남는다
        List<UserAuth> auths = userAuthRepository.findAll();
        assertThat(auths).hasSize(1);
        assertThat(auths.getFirst().getProvider()).isEqualTo("GOOGLE");
    }
}
