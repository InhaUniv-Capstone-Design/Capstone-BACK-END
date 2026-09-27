package com.gamcho.yakyeon.common.exception;

import lombok.Getter;

/**
 * 예상된 비즈니스 규칙 위반(중복 아이디, 비밀번호 불일치 등)을 표현하는 예외.
 * 서버 내부 오류(500)와 구분하기 위해 별도 타입으로 둔다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}