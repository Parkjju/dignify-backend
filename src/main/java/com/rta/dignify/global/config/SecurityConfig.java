package com.rta.dignify.global.config;

import com.rta.dignify.global.jwt.JwtProvider;
import com.rta.dignify.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtProvider jwtProvider;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    /**
     *
     * @param http 프로토타입 스코프 빈, HTTP 요청마다 독립적인 필터체인 수행, Spring Security가 주입해줌
     * @return SecurityFilterChain, WebSecurityConfiguration의 setFilterChain에서 필터체인 List에 주입
     * @throws Exception 예외
     * - SecurityFilterChain Bean 메서드가 여러개 정의되는 경우 List<SecurityFilterChain>에 같이 모여서 주입됨
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // 로그인 엔드포인트는 당연히 토큰 없이 들어온다. 여기 빠뜨리면 컨트롤러까지
                    // 가지도 못하고 필터가 AUTH_TOKEN_INVALID로 막아, 로그인이 안 되는 이유가
                    // 토큰 검증 실패처럼 보인다(구글 로그인 추가하며 실제로 밟았다).
                    .requestMatchers("/auth/apple", "/auth/google", "/auth/refresh", "/internal/**", "/feed", "/feed/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/picks", "/picks/*").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
