package com.rta.dignify.global.security;

import com.rta.dignify.global.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

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
