package com.rta.dignify.client.apple;

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
import com.rta.dignify.dto.auth.AppleIdentity;
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

@ExtendWith(MockitoExtension.class)
public class AppleAuthClientTest {

    private AppleAuthClient appleAuthClient;
    private RSAKey rsaKey;

    static class TestBuilder {
        JWSAlgorithm algorithm = JWSAlgorithm.RS256;
        String keyID = "apple";
        String issuer = "https://appleid.apple.com";
        String audience = "parkjju.dignify";
        Date expirationDate = new Date();
        String subject = "apple-user-id";
        String email = "test@gmail.com";
        RSAKey rsaKey = null;

        TestBuilder algorithm(JWSAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        TestBuilder keyID(String keyID) {
            this.keyID = keyID;
            return this;
        }

        TestBuilder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        TestBuilder audience(String audience) {
            this.audience = audience;
            return this;
        }

        TestBuilder expirationDate(Date expirationDate) {
            this.expirationDate = expirationDate;
            return this;
        }

        TestBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        TestBuilder email(String email) {
            this.email = email;
            return this;
        }

        String build() throws JOSEException {
            JWSHeader header = new JWSHeader.Builder(algorithm).keyID(keyID).build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .expirationTime(expirationDate)
                    .subject(subject)
                    .claim("email", email)
                    .build();

            if (rsaKey == null) {
                rsaKey = generateRsaKeyPair(keyID, algorithm);
            }
            RSASSASigner signer = new RSASSASigner(rsaKey);
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        }

        TestBuilder rsaKey(RSAKey rsaKey) {
            this.rsaKey = rsaKey;
            return this;
        }
    }

    @BeforeEach
    void setUp() throws JOSEException {
        appleAuthClient = new AppleAuthClient();
        rsaKey = generateRsaKeyPair("apple", JWSAlgorithm.RS256);
        ReflectionTestUtils.setField(appleAuthClient, "jwkSource", toJwkSource(rsaKey));
    }

    @Test
    @DisplayName("잘못된 형식의 토큰 전달 테스트")
    void invalidTokenTest() {
        // 잘못된 형식의 토큰 전달
        //  - ParseException / JOSEException catch
        assertThatThrownBy(() -> appleAuthClient.verifyIdentityToken("FAKE TOKEN"))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("알고리즘 불일치 케이스 테스트")
    void algorithmInconsistencyTest() {
        // 알고리즘 불일치 케이스 토큰 생성
        assertThatThrownBy(() -> {
            String token = new TestBuilder()
                    .algorithm(JWSAlgorithm.RS384)
                    .build();
            appleAuthClient.verifyIdentityToken(token);
        }).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_ALGORITHM_INVALID);
    }

    @Test
    @DisplayName("JWK List empty 케이스 테스트")
    void jwkListEmptyTest() {
        // Empty JWKSource 객체 주입
        assertThatThrownBy(() -> {
            ReflectionTestUtils.setField(appleAuthClient, "jwkSource", new ImmutableJWKSet<>(new JWKSet()));
            String token = new TestBuilder()
                    .keyID("apple")
                    .algorithm(JWSAlgorithm.RS256)
                    .rsaKey(rsaKey)
                    .build();
            appleAuthClient.verifyIdentityToken(token);
        }).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("signedJWT verify 함수 실패 케이스")
    void signedJwtVerifyFailedTest() {
        // signedJWT verify 실패 케이스 테스트
        //  - 일치하지 않는 공개 키를 기반으로 JWKSource 생성 및 주입
        assertThatThrownBy(() -> {
            RSAKey newRsaKey = generateRsaKeyPair("apple", JWSAlgorithm.RS256);

            String token = new TestBuilder()
                    .keyID("apple")
                    .algorithm(JWSAlgorithm.RS256)
                    .rsaKey(newRsaKey)
                    .build();
            appleAuthClient.verifyIdentityToken(token);
        }).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("Issuer 불일치 테스트")
    void issuerInconsistency() {
        assertThatThrownBy(() -> {
            String token = new TestBuilder()
                    .issuer("FAKE_ISSUER")
                    .rsaKey(rsaKey)
                    .build();
            appleAuthClient.verifyIdentityToken(token);
        }).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("Audience 불일치 케이스")
    void audienceInconsistency() {
        // audience 불일치 케이스
        assertThatThrownBy(() -> {
            String token = new TestBuilder()
                    .audience("FAKE_AUDIENCE")
                    .rsaKey(rsaKey)
                    .build();
            appleAuthClient.verifyIdentityToken(token);
        }).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
    }

    @Test
    @DisplayName("토큰 만료 테스트")
    void identityTokenExpiredTest() {
        // 토큰 만료 테스트
        assertThatThrownBy(() -> {
            String token = new TestBuilder()
                    .expirationDate(Date.from(Instant.now().minusMillis(10000L)))
                    .rsaKey(rsaKey)
                    .build();
            appleAuthClient.verifyIdentityToken(token);
        }).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_IDENTITY_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("정상 시나리오 테스트")
    void normalCaseTest() throws JOSEException {
        // 정상 결과 반환 테스트
        String normalToken = new TestBuilder()
                .expirationDate(Date.from(Instant.now().plusMillis(10000L)))
                .rsaKey(rsaKey)
                .subject("apple-user-id")
                .email("test@gmail.com")
                .build();
        AppleIdentity normalResponse = appleAuthClient.verifyIdentityToken(normalToken);
        assertThat(normalResponse.email()).isEqualTo("test@gmail.com");
        assertThat(normalResponse.appleId()).isEqualTo("apple-user-id");
    }

    private static RSAKey generateRsaKeyPair(String keyId, JWSAlgorithm algorithm) throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyID(keyId)
                .algorithm(algorithm)
                .generate();
    }

    private static JWKSource<SecurityContext> toJwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }
}
