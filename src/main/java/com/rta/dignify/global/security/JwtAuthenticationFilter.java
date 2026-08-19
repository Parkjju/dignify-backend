package com.rta.dignify.global.security;


import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AuthenticationEntryPoint entryPoint;
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_ERROR_STRING = "authErrorCode";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length());

            try {
                jwtProvider.validateToken(token);
                Long userId = jwtProvider.getUserId(token);

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (BusinessException e) {
                // **여기서 체인을 이으면 안 된다.** permitAll 경로(/feed, GET /picks)는 인증을
                // 요구하지 않으므로 EntryPoint가 안 불리고, 요청이 userId=null로 컨트롤러까지 간다
                // → 로그인한 유저가 200과 함께 게스트 응답(myReaction·isMine·isHyped 누락)을 받는다.
                // 401이 안 나가니 클라는 토큰을 갱신할 계기도 못 얻고, 액세스 토큰 만료(1시간) 뒤
                // 첫 요청이 조용히 익명으로 처리된다.
                //
                // AuthenticationException을 던지는 방법은 안 된다 — 이 필터는 ExceptionTranslationFilter
                // 앞이라 예외가 체인 밖으로 빠져나가 500이 된다. EntryPoint를 직접 부른다.
                request.setAttribute(AUTH_ERROR_STRING, e.getErrorCode());
                entryPoint.commence(request, response, null);
                return;
            }

        }

        filterChain.doFilter(request, response);
    }
}

