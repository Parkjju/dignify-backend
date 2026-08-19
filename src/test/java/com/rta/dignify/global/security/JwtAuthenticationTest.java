package com.rta.dignify.global.security;

import com.rta.dignify.global.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.not;

@AutoConfigureMockMvc
@SpringBootTest
public class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    private final String jwtSecret = "dzKVMJiPlHwSKRIib5vMTYXMTkLFREb4Hzds5CSFcMX";
    private final long accessTokenExpiration = 3600000L;
    private final long refreshTokenExpiration = 2592000000L;
    private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private final String invalidCode = "AUTH_TOKEN_INVALID";
    private final String expiredCode = "AUTH_TOKEN_EXPIRED";

    @Test
    @DisplayName("API 토큰 없이 요청")
    void requestWithoutToken() throws Exception {

        // /genre GET, without token
        RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/genres");
        mockMvc.perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(invalidCode));
    }

    /**
     * 로그인 엔드포인트가 permitAll에서 빠지면 필터가 먼저 막아버려, 로그인이 안 되는 원인이
     * "토큰이 잘못됐다"로 보인다. 구글 로그인을 붙이며 실제로 밟은 함정이라 두 경로 모두 박아둔다.
     * 여기서 검증하는 건 "필터를 통과했는가" 하나다 — 통과하면 토큰 검증 단계의 코드가 나온다.
     */
    @Test
    @DisplayName("로그인 엔드포인트는 토큰 없이 필터를 통과한다")
    void signInEndpointsArePermitted() throws Exception {
        for (String path : new String[]{"/auth/apple", "/auth/google"}) {
            mockMvc.perform(
                    MockMvcRequestBuilders.post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identityToken\":\"garbage\",\"idToken\":\"garbage\"}")
            ).andExpect(MockMvcResultMatchers.jsonPath("$.code").value(not(invalidCode)));
        }
    }

    @Test
    @DisplayName("API 잘못된 형식의 토큰으로 요청")
    void requestWithInvalidToken() throws Exception {
        // /genre GET with invalid token
        String garbageToken = "trash";
        RequestBuilder requestWithInvalidTokenBuilder = MockMvcRequestBuilders.get("/genres")
                .header("Authorization", "Bearer " + garbageToken);
        mockMvc.perform(requestWithInvalidTokenBuilder)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(invalidCode));
    }

    @Test
    @DisplayName("API 만료된 토큰으로 요청")
    void requestWithExpiredToken() throws Exception {
        // 3. /genre GET with expired token
        setReflectionFields(true);
        String expiredToken = jwtProvider.generateAccessToken(1L);
        RequestBuilder requestWithExpiredTokenBuilder = MockMvcRequestBuilders.get("/genres")
                .header("Authorization", "Bearer " + expiredToken);
        mockMvc.perform(requestWithExpiredTokenBuilder)
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(expiredCode));
    }

    /**
     * permitAll 경로에서 만료 토큰이 조용히 통과하던 버그의 회귀 방지.
     * 통과하면 컨트롤러가 userId=null로 돌아 로그인한 유저가 200과 함께 게스트 응답을 받고
     * (픽의 myReaction·isMine, 피드의 isHyped가 전부 비어서 내려온다),
     * 401이 안 나가니 클라가 토큰을 갱신할 계기조차 못 얻는다.
     */
    @Test
    @DisplayName("permitAll 경로도 만료된 토큰이면 401")
    void expiredTokenOnPublicEndpoint() throws Exception {
        setReflectionFields(true);
        String expiredToken = jwtProvider.generateAccessToken(1L);
        mockMvc.perform(MockMvcRequestBuilders.get("/picks")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(expiredCode));
    }

    /**
     * 위 401이 게스트까지 막으면 안 된다 — Authorization 헤더가 아예 없으면 검증할 토큰도 없다.
     * 게스트 브라우징은 App Store 5.1.1(v) 리젝을 푼 경로라 죽으면 안 된다.
     */
    @Test
    @DisplayName("permitAll 경로는 토큰이 없으면 그대로 통과")
    void noTokenOnPublicEndpoint() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/picks"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("API 정상 요청")
    void requestWithNormalToken() throws Exception {
        String normalToken = jwtProvider.generateAccessToken(1L);
        RequestBuilder requestWithNormalTokenBuilder = MockMvcRequestBuilders.get("/genres")
                .header("Authorization", "Bearer " + normalToken);
        mockMvc.perform(requestWithNormalTokenBuilder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.genres").exists());
    }

    @Test
    @DisplayName("토큰없이 요청 가능한 public API 요청 테스트")
    void requestToPublicAPI() throws Exception {
        RequestBuilder requestWithoutBody = MockMvcRequestBuilders.post("/auth/apple");
        mockMvc.perform(requestWithoutBody)
                .andExpect(MockMvcResultMatchers.status().is4xxClientError());
    }

    @BeforeEach
    void setUp() {
        setReflectionFields(false);
    }

    private void setReflectionFields(boolean invokeExpireJwt) {
        ReflectionTestUtils.setField(jwtProvider, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", accessTokenExpiration);
        ReflectionTestUtils.setField(jwtProvider, "refreshTokenExpiration", refreshTokenExpiration);

        if (invokeExpireJwt) {
            Clock pastClock = Clock.fixed(Instant.now().minusMillis(refreshTokenExpiration + 1000L), ZoneOffset.UTC);
            ReflectionTestUtils.setField(jwtProvider, "clock", pastClock);
        } else {
            ReflectionTestUtils.setField(jwtProvider, "clock", clock);
        }

        ReflectionTestUtils.invokeMethod(jwtProvider, "init"); // @PostConstruct 메서드 강제 실행
    }
}
