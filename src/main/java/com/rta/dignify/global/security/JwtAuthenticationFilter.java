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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
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
                request.setAttribute(AUTH_ERROR_STRING, e.getErrorCode());
            }

        }

        filterChain.doFilter(request, response);
    }
}

