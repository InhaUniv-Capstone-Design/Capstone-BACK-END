package com.gamcho.yakyeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 로직에서 발생하는 예외 코드 모음.
 * REST API 명세 §0 공통 에러 응답 포맷({ code, message })의 code 값으로 사용된다.
 */
public enum ErrorCode {

    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    INVALID_ACCOUNT_TYPE(HttpStatus.BAD_REQUEST, "계정 유형은 SELF 또는 GUARDIAN만 가능합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}