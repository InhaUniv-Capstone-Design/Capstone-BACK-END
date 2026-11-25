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

    /**
     * 상황별 메시지를 직접 지정한다. 여러 항목을 한 번에 받는 요청에서
     * "2번째 약의 어떤 값이 문제인지"처럼 구체적으로 알려줄 때 사용한다.
     * 응답의 code와 HTTP 상태는 ErrorCode 그대로이고 message만 바뀐다.
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}