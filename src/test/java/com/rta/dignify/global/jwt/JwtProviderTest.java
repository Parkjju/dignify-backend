package com.rta.dignify.global.jwt;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class JwtProviderTest {
    // 테스트용 랜덤 생성된 secret
    private final String jwtSecret = "dzKVMJiPlHwSKRIib5vMTYXMTkLFREb4Hzds5CSFcMX";
    private SecretKey secretKey;
    private final long accessTokenExpiration = 3600000L;
    private final long refreshTokenExpiration = 2592000000L;
    private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

    private JwtProvider jwtProvider;

    @Test
    @DisplayName("generate access / refresh 토큰 테스트")
    void tokenGenerateTest() {
        String generatedAccessToken = jwtProvider.generateAccessToken(1L);
        Claims accesTokenClaims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(generatedAccessToken)
                .getPayload();
        assertThat(Long.valueOf(accesTokenClaims.getSubject())).isEqualTo(1L);
        assertThat(accesTokenClaims.getIssuedAt()).isCloseTo(Date.from(clock.instant()), 1000);
        assertThat(accesTokenClaims.getExpiration()).isCloseTo(Date.from(clock.instant().plusMillis(accessTokenExpiration)), 1000);

        String generatedRefreshToken = jwtProvider.generateRefreshToken(1L);
        Claims refreshTokenClaims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(generatedRefreshToken)
                .getPayload();
        assertThat(Long.valueOf(refreshTokenClaims.getSubject())).isEqualTo(1L);
        assertThat(refreshTokenClaims.getIssuedAt()).isCloseTo(Date.from(clock.instant()), 1000);
        assertThat(refreshTokenClaims.getExpiration()).isCloseTo(Date.from(clock.instant().plusMillis(refreshTokenExpiration)), 1000);
    }

    @Test
    @DisplayName("invalid 토큰 입력 테스트")
    void invalidTokenTest() {
        // 1. 유효하지 않은 토큰
        String invalidToken = "invalidToken";
        assertThatThrownBy(() -> jwtProvider.validateToken(invalidToken))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    @DisplayName("만료된 토큰 입력 테스트")
    void expiredTokenTest() {
        // 2. 만료 검증 테스트
        //  - 현재 시점 기준으로 토큰 발급
        //  - 미래 시점으로 Clock 수정
        //  - validate시 현재 시각 기준으로 검증하므로 EXPIRED 예외 던짐
        Clock pastClock = Clock.fixed(clock.instant().minusMillis(accessTokenExpiration + 100000L), ZoneOffset.UTC);
        jwtProvider = new JwtProvider(pastClock);
        setReflectionFields();
        String expiredAccessToken = jwtProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.validateToken(expiredAccessToken))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("정상 토큰 입력 테스트")
    void normalTokenTest() {
        // 3. 정상 검증 테스트
        //  - 미래 시점에 만료되는 토큰 미리 발급해놓기.
        //  - 현재 시점으로 Clock 수정해놓기
        //  - validate시 현재 시각 기준으로 검증하므로 Exception 발생 안함
        //  - 유효한 토큰 형식이므로 INVALID 발생 안함
        String normalAccessToken = jwtProvider.generateAccessToken(1L);
        Clock currentClock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        jwtProvider = new JwtProvider(currentClock);
        setReflectionFields();
        assertThatCode(() -> jwtProvider.validateToken(normalAccessToken))
                .doesNotThrowAnyException();
    }

    private void setReflectionFields() {
        ReflectionTestUtils.setField(jwtProvider, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", accessTokenExpiration);
        ReflectionTestUtils.setField(jwtProvider, "refreshTokenExpiration", refreshTokenExpiration);
        ReflectionTestUtils.invokeMethod(jwtProvider, "init"); // @PostConstruct 메서드 강제 실행
    }

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(clock);
        secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        setReflectionFields();
    }
}
