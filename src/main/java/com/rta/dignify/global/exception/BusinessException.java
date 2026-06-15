package com.rta.dignify.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    /**
     *
     * @param errorCode 에러 코드, e.errorCode.getMessage로 이미 정의된 고정 에러메시지 출력 가능
     * @param detail    BusinessException 생성 후 예외를 던질때 정의해야 하는 메시지값, BusinessException e.getMessage로 접근 가능
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    /**
     * detail 메시지 정의를 하지 않기 때문에 에러코드의 메시지를 그대로 사용한다.
     *
     * @param errorCode detail 메시지 없이 ErrorCode만 정의 후 에외 던질때 사용
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
