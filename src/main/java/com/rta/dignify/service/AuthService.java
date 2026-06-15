package com.rta.dignify.service;

import com.rta.dignify.client.apple.AppleAuthClient;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserAuth;
import com.rta.dignify.domain.UserToken;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.dto.auth.AuthTokenResponse;
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
@Transactional(readOnly = true)
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
    @Transactional(readOnly = false)
    public AuthTokenResponse signInWithApple(String identityToken) {
        AppleIdentity appleIdentity = appleAuthClient.verifyIdentityToken(identityToken);
        String email = appleIdentity.email();
        String appleId = appleIdentity.appleId();

        Optional<UserAuth> userAuth = userAuthRepository.findByProviderAndProviderUserId(PROVIDER.APPLE.name(), appleId);
        User user;

        // 1. 애플 프로바이더 기준 유저가 존재하지 않는경우 회원가입
        // 2. 존재하는 경우 로그인
        if (userAuth.isEmpty()) {
            // 랜덤 닉네임 생성
            String nickname = generateUniqueNickname();

            user = User.create(email, nickname);
            userRepository.save(user);

            UserAuth newUserAuth = UserAuth.create(user, PROVIDER.APPLE.name(), appleId);
            userAuthRepository.save(newUserAuth);
        } else {
            // User 조회쿼리 수행 X, 프록시로부터 id값만 반환받음
            user = userAuth.get().getUser();
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        Instant accessTokenExpiresAt = Instant.now().plusMillis(jwtProvider.getAccessTokenExpiration());
        Instant refreshTokenExpiresAt = Instant.now().plusMillis(jwtProvider.getRefreshTokenExpiration());

        UserToken userToken = UserToken.create(user, TokenHasher.hash(refreshToken), refreshTokenExpiresAt);
        userTokenRepository.save(userToken);

        return new AuthTokenResponse(refreshToken, accessToken, accessTokenExpiresAt);
    }

    private String generateUniqueNickname() {
        String nickname;
        do {
            nickname = "digger_" + UUID.randomUUID().toString().substring(0, 8);
        } while (userRepository.existsByNickname(nickname));
        return nickname;
    }
}
