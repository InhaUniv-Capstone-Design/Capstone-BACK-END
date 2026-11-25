package com.gamcho.yakyeon.common.exception;

/**
 * REST API 명세 §0의 공통 에러 응답 포맷: { "code": "...", "message": "..." }
 */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getDefaultMessage());
    }
}