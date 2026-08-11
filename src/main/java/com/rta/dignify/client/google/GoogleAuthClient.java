package com.rta.dignify.client.google;

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
import com.rta.dignify.dto.auth.GoogleIdentity;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Set;

/**
 * Google ID 토큰 검증. 검증 절차는 {@link com.rta.dignify.client.apple.AppleAuthClient}와 같다
 * (둘 다 RS256 서명된 OIDC ID 토큰이라 JWKS로 공개키를 받아 검증한다). 다른 건 셋뿐이다.
 * <ul>
 *   <li>JWKS 주소와 issuer가 구글 것</li>
 *   <li>issuer를 두 가지로 받는다 — 구글이 {@code accounts.google.com}과
 *       {@code https://accounts.google.com}을 둘 다 발급한다</li>
 *   <li>audience가 하드코딩이 아니라 설정값 — 안드로이드 앱이 쓰는 <b>웹</b> OAuth 클라이언트 ID이고,
 *       릴리스/디버그를 갈아끼울 수 있어야 한다</li>
 * </ul>
 * 두 클라이언트를 공통 부모로 묶지 않은 이유: 지금 겹치는 건 nimbus 호출 순서뿐이고,
 * 실제로 다른 부분(issuer 두 형태, email_verified)이 각자 있어 추상화하면 분기가 부모로 올라간다.
 */
@Slf4j
@Component
public class GoogleAuthClient {
    private JWKSource<SecurityContext> jwkSource;

    private static final String KEY_ADDRESS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    /**
     * 안드로이드 앱이 Credential Manager에 넘기는 <b>웹</b> 클라이언트 ID.
     * 구글이 발급하는 ID 토큰의 aud가 이 값이라, 여기가 비어 있으면 어떤 토큰도 통과하지 못한다.
     * (기동은 시키되 로그인만 막는다 — 아직 콘솔에 클라이언트를 안 만든 상태에서도 배포는 돼야 한다.)
     */
    @Value("${google.client-id:}")
    private String clientId;

    /**
     * 하드코딩된 주소가 잘못된 경우 빈 생성 단계에서 터지도록 의도한 것. AppleAuthClient와 같다.
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

    public GoogleIdentity verifyIdToken(String idToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(idToken);
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

            if (!GOOGLE_ISSUERS.contains(jwtClaimsSet.getIssuer())) {
                log.warn("ID Token iss 불일치");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
            }

            // clientId 미설정 상태에서 aud 검사를 건너뛰면 아무 구글 앱의 토큰이나 통과한다.
            // 남의 앱 토큰으로 우리 계정에 로그인할 수 있다는 뜻이라 빈 값은 검사 통과가 아니라 거부다.
            List<String> aud = jwtClaimsSet.getAudience();
            if (clientId == null || clientId.isBlank() || aud == null || !aud.contains(clientId)) {
                log.warn("ID Token aud 불일치 (google.client-id 미설정 여부 확인)");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
            }

            Date exp = jwtClaimsSet.getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                log.warn("ID Token 만료");
                throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_EXPIRED);
            }

            String email = jwtClaimsSet.getStringClaim("email");
            // email_verified는 문자열("true")로 오는 경우도 있어 Boolean 캐스팅에 기대지 않는다.
            Object verified = jwtClaimsSet.getClaim("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));

            return new GoogleIdentity(email, jwtClaimsSet.getSubject(), emailVerified);
        } catch (ParseException | JOSEException e) {
            log.warn("ID Token 처리 중 예외 발생: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_IDENTITY_TOKEN_INVALID);
        }
    }
}
