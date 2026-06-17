package com.rta.dignify.client.apple;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rta.dignify.dto.auth.AppleIdentity;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class AppleAuthClient {
    private JWKSource<SecurityContext> jwkSource;

    private static final String KEY_ADDRESS = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_HOST = "https://appleid.apple.com";
    private static final String APP_ID = "parkjju.dignify";

    /**
     * 아래 두 예외는 빈 생성 오류로 application 생성 레벨에서 예외 발생하는 것을 의도
     * 잘못 하드코딩된 주소값을 사용하고 있는 것을 인지
     *
     * @throws URISyntaxException URI 형식 예외
     * @throws MalformedURLException URL 형식 예외
     */
    @PostConstruct
    public void init() throws URISyntaxException, MalformedURLException {
        URL url = new URI(KEY_ADDRESS).toURL();
        this.jwkSource = JWKSourceBuilder.<SecurityContext>create(url)
                .cache(Duration.ofDays(1).toMillis(), JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT)
                .build();
    }

    public AppleIdentity verifyIdentityToken(String identityToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(identityToken);
            Algorithm alg = signedJWT.getHeader().getAlgorithm();

            if (alg == null || !alg.equals(JWSAlgorithm.RS256)) {
                log.warn("JWS 알고리즘 불일치");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_ALGORITHM_INVALID);
            }

            String kid = signedJWT.getHeader().getKeyID();
            JWKMatcher jwkMatcher = new JWKMatcher.Builder().keyID(kid).keyType(KeyType.RSA).build();
            JWKSelector jwkSelector = new JWKSelector(jwkMatcher);

            List<JWK> listOfJwk = jwkSource.get(jwkSelector, null);
            if (listOfJwk == null || listOfJwk.isEmpty()) {
                log.warn("JWK List is Empty");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
            }

            JWK jwk = listOfJwk.getFirst();
            RSAKey rsaKey = (RSAKey) jwk;
            RSAPublicKey rsaPublicKey = rsaKey.toRSAPublicKey();
            JWSVerifier jwsVerifier = new RSASSAVerifier(rsaPublicKey);

            if (!signedJWT.verify(jwsVerifier)) {
                log.warn("signedJWT verify 실패");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
            }

            JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();

            if (!APPLE_HOST.equals(jwtClaimsSet.getIssuer())) {
                log.warn("Identity Token iss 불일치");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
            }

            List<String> aud = jwtClaimsSet.getAudience();
            if (aud == null || !aud.contains(APP_ID)) {
                log.warn("Identity Token appId 불일치");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
            }

            // 이미 만료된 identityToken
            Date exp = jwtClaimsSet.getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                log.warn("Identity Token 만료");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_EXPIRED);
            }

            return new AppleIdentity(jwtClaimsSet.getStringClaim("email"), jwtClaimsSet.getSubject());
        } catch (ParseException | JOSEException e) {
            log.warn("Identity Token 처리 중 예외 발생: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
        }
    }
}
