package com.rta.dignify.service;

import com.rta.dignify.client.apple.AppleAuthClient;
import com.rta.dignify.client.google.GoogleAuthClient;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserAuth;
import com.rta.dignify.domain.UserToken;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.dto.auth.AuthTokenResponse;
import com.rta.dignify.dto.auth.GoogleIdentity;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.global.jwt.JwtProvider;
import com.rta.dignify.global.util.TokenHasher;
import com.rta.dignify.repository.UserAuthRepository;
import com.rta.dignify.repository.UserRepository;
import com.rta.dignify.repository.UserTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final UserAuthRepository userAuthRepository;
    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;

    private final JwtProvider jwtProvider;
    private final AppleAuthClient appleAuthClient;
    private final GoogleAuthClient googleAuthClient;

    private enum PROVIDER {
        APPLE, GOOGLE
    }

    /**
     *
     * @param identityToken Apple 로그인 IdentityToken
     * @return accessToken, refreshToken, 액세스 토큰 만료시간
     */
    @Transactional
    public AuthTokenResponse signInWithApple(String identityToken) {
        AppleIdentity appleIdentity = appleAuthClient.verifyIdentityToken(identityToken);
        String email = appleIdentity.email();
        String appleId = appleIdentity.appleId();

        Optional<User> findingUser = userAuthRepository.findUserByProviderAndProviderUserId(PROVIDER.APPLE.name(), appleId);

        User user;

        // 1. 애플 프로바이더 기준 유저가 존재하지 않는경우 회원가입
        // 2. 존재하는 경우 로그인
        if (findingUser.isEmpty()) {
            user = saveNewUserAndAuth(email, PROVIDER.APPLE.name(), appleId);
        } else {
            user = findingUser.get();
            // 삭제된 유저가 apple login으로 재가입
            // soft delete 데이터 전체 삭제 후 가입처리
            if (user.getDeletedAt() != null) {
                // Apple은 재로그인 토큰에 email을 넣어주지 않으므로 기존 유저 email 재사용
                String existingEmail = user.getEmail();
                userRepository.delete(user);
                // Hibernate 기본 flush 순서는 INSERT가 DELETE보다 먼저라, 같은 email/appleId로
                // 재가입 시 unique 제약에 걸린다. delete를 먼저 반영하도록 강제 flush.
                userRepository.flush();
                user = saveNewUserAndAuth(existingEmail, PROVIDER.APPLE.name(), appleId);
            }
        }

        return issueTokens(user);
    }

    /**
     * 구글 로그인(안드로이드). 애플과 다른 점은 <b>이메일로 기존 계정에 연결</b>한다는 것 하나다.
     * <p>
     * user_auth가 한 유저에 인증수단 여러 개를 매달 수 있게 돼 있어서, iOS에서 애플로 가입한 사람이
     * 안드로이드에서 같은 이메일로 구글 로그인하면 새 계정이 아니라 <b>같은 계정에 붙는다</b>.
     * 연결하지 않으면 email이 unique라 가입 자체가 제약 위반으로 터진다.
     * <p>
     * 다만 애플 "이메일 가리기"로 가입한 유저는 {@code @privaterelay.appleid.com}이라 구글 이메일과
     * 절대 안 겹친다 → 별도 계정이 된다. 자동으로는 못 푸는 문제라 그대로 둔다(수동 연결 UI가 있어야 함).
     *
     * @param idToken Credential Manager가 돌려준 Google ID 토큰
     * @return accessToken, refreshToken, 액세스 토큰 만료시간
     */
    @Transactional
    public AuthTokenResponse signInWithGoogle(String idToken) {
        GoogleIdentity identity = googleAuthClient.verifyIdToken(idToken);
        String email = identity.email();
        String googleId = identity.googleId();

        // 이메일이 이 계정의 열쇠가 되므로, 검증 안 됐거나 아예 없으면 로그인을 거절한다.
        // 여기를 통과시키면 남의 이메일을 자칭하는 토큰으로 그 사람 계정에 연결할 수 있다.
        if (!identity.emailVerified() || email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        String provider = PROVIDER.GOOGLE.name();
        Optional<User> byProvider = userAuthRepository.findUserByProviderAndProviderUserId(provider, googleId);

        User user;
        if (byProvider.isPresent()) {
            user = byProvider.get();
            // 탈퇴 후 재로그인 — 애플과 같은 정책으로 연관 데이터를 cascade hard delete 후 재가입.
            if (user.getDeletedAt() != null) {
                user = recreate(user, email, provider, googleId);
            }
        } else {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isEmpty()) {
                user = saveNewUserAndAuth(email, provider, googleId);
            } else if (byEmail.get().getDeletedAt() != null) {
                // 탈퇴한 계정이 쓰던 이메일. 되살리지 않고 지운 뒤 새로 만든다(애플 재가입과 같은 판단).
                user = recreate(byEmail.get(), email, provider, googleId);
            } else {
                // 같은 이메일의 살아있는 계정 → 계정 연결. 유저는 그대로 두고 인증수단만 하나 더 단다.
                user = byEmail.get();
                userAuthRepository.save(UserAuth.create(user, provider, googleId));
            }
        }

        return issueTokens(user);
    }

    /**
     * soft delete된 유저를 지우고 같은 이메일로 새로 가입시킨다.
     * Hibernate 기본 flush 순서는 INSERT가 DELETE보다 먼저라, flush를 강제하지 않으면
     * 같은 email로 재가입할 때 unique 제약에 걸린다(애플 쪽에서 먼저 밟은 함정이다).
     */
    private User recreate(User deleted, String email, String provider, String providerUserId) {
        userRepository.delete(deleted);
        userRepository.flush();
        return saveNewUserAndAuth(email, provider, providerUserId);
    }

    /** 로그인 성공 후 토큰 발급 + refresh 토큰 저장. 프로바이더와 무관한 공통 절차. */
    private AuthTokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        Instant accessTokenExpiresAt = Instant.now().plusMillis(jwtProvider.getAccessTokenExpiration());
        Instant refreshTokenExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());

        UserToken userToken = UserToken.create(user, TokenHasher.hash(refreshToken), refreshTokenExpiresAt);
        userTokenRepository.save(userToken);

        return new AuthTokenResponse(refreshToken, accessToken, accessTokenExpiresAt);
    }

    /**
     *
     * @param refreshToken 유저 리프레시 토큰
     * @return 갱신된 리프레시 토큰
     * @throws BusinessException 401
     */
    @Transactional
    public AuthTokenResponse refreshToken(String refreshToken) {
        jwtProvider.validateToken(refreshToken);
        // refresh token 기준으로 DB 조회
        String hashedRefreshToken = TokenHasher.hash(refreshToken);

        // throw BusinessException - DB상의 expiresAt 컬럼으로 인해 USER_TOKEN 테이블에서 정리된 케이스
        UserToken userToken = userTokenRepository.findUserTokenByRefreshTokenHash(hashedRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));

        String newRefreshToken = jwtProvider.generateRefreshToken(userToken.getUser().getId());
        String newAccessToken = jwtProvider.generateAccessToken(userToken.getUser().getId());
        Instant refreshTokenExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());
        Instant accessTokenExpiresAt = Instant.now().plusMillis(jwtProvider.getAccessTokenExpiration());

        String hashedNewRefreshToken = TokenHasher.hash(newRefreshToken);

        // 리프레시 토큰 및 만료시각 갱신 (sliding token)
        userToken.rotate(hashedNewRefreshToken, refreshTokenExpiresAt);

        return new AuthTokenResponse(newRefreshToken, newAccessToken, accessTokenExpiresAt);
    }

    /**
     *
     * @param refreshToken UserToken 엔티티 조회를 위한 리프레시 토큰값
     */
    @Transactional
    public void logout(String refreshToken) {
        String hashedRefreshToken = TokenHasher.hash(refreshToken);
        userTokenRepository.deleteUserTokenByRefreshTokenHash(hashedRefreshToken);
    }

    /**
     *
     * @param refreshToken UserToken 엔티티 조회를 위한 리프레시 토큰
     */
    @Transactional
    public void withdraw(String refreshToken) {
        String hashedRefreshToken = TokenHasher.hash(refreshToken);

        // throw BusinessException - DB상의 expiresAt 컬럼으로 인해 USER_TOKEN 테이블에서 정리된 케이스
        UserToken userToken = userTokenRepository.findUserTokenByRefreshTokenHash(hashedRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        User user = userToken.getUser();

        // 멀티 디바이스 고려하여 userID 기준 모든 토큰 정보 삭제
        userTokenRepository.deleteAllByUser(user);

        // 토큰정보 삭제 후 유저 SOFT DELETE
        user.deleteUser();
    }

    private User saveNewUserAndAuth(String email, String provider, String providerUserId) {
        // 랜덤 닉네임 생성
        String nickname = generateUniqueNickname();

        User user = User.create(email, nickname);
        userRepository.save(user);

        UserAuth newUserAuth = UserAuth.create(user, provider, providerUserId);
        userAuthRepository.save(newUserAuth);

        return user;
    }

    private String generateUniqueNickname() {
        String nickname;
        do {
            nickname = "digger_" + UUID.randomUUID().toString().substring(0, 8);
        } while (userRepository.existsByNickname(nickname));
        return nickname;
    }
}
