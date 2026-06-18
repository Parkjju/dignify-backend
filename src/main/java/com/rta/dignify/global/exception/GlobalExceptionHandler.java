package com.rta.dignify.global.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     *
     * @param e DataIntegrityViolation 예외
     * @return 409 응답
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler
    public ErrorResponse handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return ErrorResponse.from(ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    /**
     *
     * @param e MethodArgumentNotValid 예외
     * @return 400 응답
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    public ErrorResponse handleMethodArgumentException(MethodArgumentNotValidException e) {
        return ErrorResponse.from(ErrorCode.METHOD_ARGUMENT_NOT_VALID);
    }

    /**
     *
     * @param e 비즈니스 로직 예외, 런타임에 명시적으로 BusinessException 생성 후 예외로 던짐
     * @return 각 BusinessException 내의 ErrorCode를 가지고 동적으로 상태코드 응답
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorResponse errorResponse = ErrorResponse.from(e.getErrorCode());
        return new ResponseEntity<>(errorResponse, e.getErrorCode().getHttpStatus());
    }

    /**
     *
     * @param e POST 요청에서 메시지 바디가 비어있을때 발생하는 예외 처리
     * @return HttpMessageNotReadableException
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ErrorResponse.from(ErrorCode.METHOD_ARGUMENT_NOT_VALID);
    }

    /**
     *
     * @param e 알 수 없는 서버 예외
     * @return 500 예외
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler
    public ErrorResponse handleInternalServerException(Exception e) {
        return ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
