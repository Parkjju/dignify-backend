package com.rta.dignify.client.google;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rta.dignify.dto.auth.GoogleIdentity;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AppleAuthClientTest와 같은 구조. 구글에만 있는 케이스가 셋 붙는다 —
 * issuer 두 형태, email_verified 문자열/불리언, client-id 미설정.
 */
@ExtendWith(MockitoExtension.class)
public class GoogleAuthClientTest {

    private static final String CLIENT_ID = "test-web-client-id.apps.googleusercontent.com";

    private GoogleAuthClient googleAuthClient;
    private RSAKey rsaKey;

    static class TestBuilder {
        JWSAlgorithm algorithm = JWSAlgorithm.RS256;
        String keyID = "google";
        String issuer = "https://accounts.google.com";
        String audience = CLIENT_ID;
        Date expirationDate = Date.from(Instant.now().plusMillis(10000L));
        String subject = "google-user-id";
        String email = "test@gmail.com";
        Object emailVerified = Boolean.TRUE;
        RSAKey rsaKey = null;

        TestBuilder algorithm(JWSAlgorithm algorithm) { this.algorithm = algorithm; return this; }
        TestBuilder keyID(String keyID) { this.keyID = keyID; return this; }
        TestBuilder issuer(String issuer) { this.issuer = issuer; return this; }
        TestBuilder audience(String audience) { this.audience = audience; return this; }
        TestBuilder expirationDate(Date expirationDate) { this.expirationDate = expirationDate; return this; }
        TestBuilder subject(String subject) { this.subject = subject; return this; }
        TestBuilder email(String email) { this.email = email; return this; }
        TestBuilder emailVerified(Object emailVerified) { this.emailVerified = emailVerified; return this; }
        TestBuilder rsaKey(RSAKey rsaKey) { this.rsaKey = rsaKey; return this; }

        String build() throws JOSEException {
            JWSHeader header = new JWSHeader.Builder(algorithm).keyID(keyID).build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .expirationTime(expirationDate)
                    .subject(subject)
                    .claim("email", email)
                    .claim("email_verified", emailVerified)
                    .build();

            if (rsaKey == null) {
                rsaKey = generateRsaKeyPair(keyID, algorithm);
            }
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(rsaKey));
            return signedJWT.serialize();
        }
    }

    @BeforeEach
    void setUp() throws JOSEException {
        googleAuthClient = new GoogleAuthClient();
        rsaKey = generateRsaKeyPair("google", JWSAlgorithm.RS256);
        ReflectionTestUtils.setField(googleAuthClient, "jwkSource", toJwkSource(rsaKey));
        ReflectionTestUtils.setField(googleAuthClient, "clientId", CLIENT_ID);
    }

    @Test
    @DisplayName("잘못된 형식의 토큰 전달 테스트")
    void invalidTokenTest() {
        assertThatThrownBy(() -> googleAuthClient.verifyIdToken("FAKE TOKEN"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("알고리즘 불일치 케이스 테스트")
    void algorithmInconsistencyTest() {
        assertThatThrownBy(() -> {
            String token = new TestBuilder().algorithm(JWSAlgorithm.RS384).build();
            googleAuthClient.verifyIdToken(token);
        }).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_ALGORITHM_INVALID);
    }

    @Test
    @DisplayName("JWK List empty 케이스 테스트")
    void jwkListEmptyTest() {
        assertThatThrownBy(() -> {
            ReflectionTestUtils.setField(googleAuthClient, "jwkSource", new ImmutableJWKSet<>(new JWKSet()));
            String token = new TestBuilder().rsaKey(rsaKey).build();
            googleAuthClient.verifyIdToken(token);
        }).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("서명 검증 실패 케이스 - 다른 키로 서명된 토큰")
    void signedJwtVerifyFailedTest() {
        assertThatThrownBy(() -> {
            RSAKey otherKey = generateRsaKeyPair("google", JWSAlgorithm.RS256);
            String token = new TestBuilder().rsaKey(otherKey).build();
            googleAuthClient.verifyIdToken(token);
        }).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("Issuer 불일치 테스트")
    void issuerInconsistency() {
        assertThatThrownBy(() -> {
            String token = new TestBuilder().issuer("FAKE_ISSUER").rsaKey(rsaKey).build();
            googleAuthClient.verifyIdToken(token);
        }).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("Issuer는 두 형태 모두 허용 - 구글이 스킴 없는 accounts.google.com도 발급한다")
    void bothIssuerFormsAccepted() throws JOSEException {
        String token = new TestBuilder().issuer("accounts.google.com").rsaKey(rsaKey).build();
        assertThat(googleAuthClient.verifyIdToken(token).googleId()).isEqualTo("google-user-id");
    }

    @Test
    @DisplayName("Audience 불일치 케이스 - 다른 앱의 클라이언트 ID")
    void audienceInconsistency() {
        assertThatThrownBy(() -> {
            String token = new TestBuilder().audience("other-app.apps.googleusercontent.com").rsaKey(rsaKey).build();
            googleAuthClient.verifyIdToken(token);
        }).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("client-id 미설정이면 통과가 아니라 거부 - 아무 앱 토큰이나 받으면 안 된다")
    void clientIdMissingRejects() {
        assertThatThrownBy(() -> {
            ReflectionTestUtils.setField(googleAuthClient, "clientId", "");
            String token = new TestBuilder().rsaKey(rsaKey).build();
            googleAuthClient.verifyIdToken(token);
        }).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("토큰 만료 테스트")
    void idTokenExpiredTest() {
        assertThatThrownBy(() -> {
            String token = new TestBuilder()
                    .expirationDate(Date.from(Instant.now().minusMillis(10000L)))
                    .rsaKey(rsaKey)
                    .build();
            googleAuthClient.verifyIdToken(token);
        }).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("정상 시나리오 테스트")
    void normalCaseTest() throws JOSEException {
        String token = new TestBuilder().rsaKey(rsaKey).build();

        GoogleIdentity identity = googleAuthClient.verifyIdToken(token);
        assertThat(identity.email()).isEqualTo("test@gmail.com");
        assertThat(identity.googleId()).isEqualTo("google-user-id");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("email_verified가 문자열 \"true\"로 와도 검증된 것으로 읽는다")
    void emailVerifiedAsString() throws JOSEException {
        String token = new TestBuilder().emailVerified("true").rsaKey(rsaKey).build();
        assertThat(googleAuthClient.verifyIdToken(token).emailVerified()).isTrue();
    }

    @Test
    @DisplayName("email_verified false는 그대로 false로 전달 - 거절 판단은 서비스가 한다")
    void emailVerifiedFalseIsPassedThrough() throws JOSEException {
        String token = new TestBuilder().emailVerified(Boolean.FALSE).rsaKey(rsaKey).build();
        assertThat(googleAuthClient.verifyIdToken(token).emailVerified()).isFalse();
    }

    private static RSAKey generateRsaKeyPair(String keyId, JWSAlgorithm algorithm) throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyID(keyId)
                .algorithm(algorithm)
                .generate();
    }

    private static JWKSource<SecurityContext> toJwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey.toPublicJWK()));
    }
}
