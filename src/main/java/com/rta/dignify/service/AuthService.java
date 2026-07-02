package com.rta.dignify.service;

import com.rta.dignify.client.apple.AppleAuthClient;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserAuth;
import com.rta.dignify.domain.UserToken;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.dto.auth.AuthTokenResponse;
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
            user = saveNewUserAndAuth(email, appleId);
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
                user = saveNewUserAndAuth(existingEmail, appleId);
            }
        }

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

    private User saveNewUserAndAuth(String email, String appleId) {
        // 랜덤 닉네임 생성
        String nickname = generateUniqueNickname();

        User user = User.create(email, nickname);
        userRepository.save(user);

        UserAuth newUserAuth = UserAuth.create(user, PROVIDER.APPLE.name(), appleId);
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
