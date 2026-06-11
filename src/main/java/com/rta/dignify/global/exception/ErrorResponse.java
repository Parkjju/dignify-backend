package com.rta.dignify.global.exception;

/**
 * @param code errorCode.name() 호출하여 클라이언트 에러 응답에 enum constant 문자열 값 입력
 * @param message 에러 응답 디테일 메시지 정의 또는 errorCode.getMessage / e.getMessage 입력
 */
public record ErrorResponse(String code, String message) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }
}
