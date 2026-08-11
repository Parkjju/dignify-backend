package com.rta.dignify.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // USER domain
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾지 못했습니다."),
    USER_NICKNAME_DUPLICATE(HttpStatus.CONFLICT, "유저 닉네임이 이미 사용중입니다."),
    USER_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 닉네임입니다."),

    // FEED domain
    CURSOR_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 커서값입니다."),

    // Hype
    HYPE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 하입이 등록되어 있습니다."),
    HYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 하입 대상입니다."),

    // track domain
    TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "트랙을 찾지 못했습니다."),

    // Genre domain
    GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "장르를 찾지 못했습니다."),

    // Cron State domain
    CRON_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 job name을 갖는 크론잡을 찾을 수 없습니다."),
    CRON_JOB_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "크론잡 processBatch 실행에 실패했습니다. (process result null 리턴)"),
    CRON_SECRET_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 크론잡 시크릿값입니다."),

    // Auth domain
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 정보가 만료되었습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_IDENTITY_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 identity 토큰입니다."),
    AUTH_IDENTITY_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 identity 토큰입니다."),
    AUTH_IDENTITY_TOKEN_ALGORITHM_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 identity 토큰 알고리즘입니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    // 구글 로그인은 이메일로 기존 계정을 찾아 연결하므로, 검증 안 된 이메일을 받으면
    // 남의 계정에 붙을 수 있다. 그래서 연결이 아니라 로그인 자체를 거절한다.
    AUTH_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "이메일이 검증되지 않은 계정입니다."),

    // Artist Request domain
    ARTIST_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "등록 요청이 존재하지 않습니다."),

    // Pick domain
    PICK_DOES_NOT_EXIST(HttpStatus.NOT_FOUND, "픽이 존재하지 않습니다."),
    PICK_TITLE_BLOCKED(HttpStatus.BAD_REQUEST, "입력하신 내용에 제한이 되는 단어가 포함되어 있습니다."),

    // Reaction domain
    INVALID_EMOJI(HttpStatus.BAD_REQUEST, "유효하지 않은 이모지입니다."),

    // common domain
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 에러가 발생했습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "요청 메서드가 잘못되었습니다."),
    METHOD_ARGUMENT_NOT_VALID(HttpStatus.BAD_REQUEST, "요청 메서드 입력값이 잘못되었습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "데이터 입력 시 충돌이 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
