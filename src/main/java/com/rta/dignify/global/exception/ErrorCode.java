package com.rta.dignify.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // USER domain
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾지 못했습니다."),
    USER_NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "유저 닉네임이 이미 사용중입니다."),
    USER_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 닉네임입니다."),

    // Auth domain
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,  "인증 정보가 만료되었습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // 500 common domain
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 에러가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
