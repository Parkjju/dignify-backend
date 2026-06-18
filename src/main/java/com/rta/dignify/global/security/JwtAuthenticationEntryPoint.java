package com.rta.dignify.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_STRING);
        if (errorCode == null) { errorCode = ErrorCode.AUTH_TOKEN_INVALID; }
        ErrorResponse errorResponse = ErrorResponse.from(errorCode);
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String jsonString = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonString);
    }
}
