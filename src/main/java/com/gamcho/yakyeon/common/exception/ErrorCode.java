package com.gamcho.yakyeon.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 로직에서 발생하는 예외 코드 모음.
 * REST API 명세 §0 공통 에러 응답 포맷({ code, message })의 code 값으로 사용된다.
 */
public enum ErrorCode {

    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    INVALID_ACCOUNT_TYPE(HttpStatus.BAD_REQUEST, "계정 유형은 PATIENT 또는 GUARDIAN만 가능합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."),

    // --- 약관 동의 (FR-AUTH-008) ---
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해야 가입할 수 있습니다."),
    TERMS_VERSION_OUTDATED(HttpStatus.CONFLICT, "약관 내용이 갱신되었습니다. 최신 약관을 다시 확인해주세요."),
    TERMS_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "약관 데이터가 설정되지 않았습니다. 관리자에게 문의해주세요."),

    // --- 로그인/토큰 (FR-AUTH-005~007) ---
    /** 아이디가 없거나 비밀번호가 틀렸거나 - 둘을 구분해서 알려주지 않는다 (계정 존재 여부 비노출) */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다. 다시 로그인해주세요."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "비정상적인 접근이 감지되어 모든 로그인이 종료되었습니다. 다시 로그인해주세요."),

    // --- 비밀번호 변경 / 계정 삭제 ---
    CURRENT_PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다."),

    // --- 복약자 등록 (FR-AUTH-009, 011, 018) ---
    PATIENT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 본인 복약자 프로필이 등록되어 있습니다."),
    PATIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 복약자입니다."),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "휴대폰 번호 형식이 올바르지 않습니다. (예: 01012345678)"),

    // --- 보호자 대리 동의 (FR-AUTH-009~015) ---
    GUARDIAN_ONLY(HttpStatus.FORBIDDEN, "보호자(GUARDIAN) 계정만 사용할 수 있는 기능입니다."),
    GUARDIAN_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 연동 요청입니다."),
    GUARDIAN_LINK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 대기 중이거나 연동된 요청이 있습니다."),
    GUARDIAN_LINK_NOT_PENDING(HttpStatus.CONFLICT, "동의 대기 중인 연동 요청이 아닙니다."),
    GUARDIAN_LINK_NOT_ACTIVE(HttpStatus.CONFLICT, "연동 중인 상태가 아닙니다."),
    PERMISSION_UPGRADE_FORBIDDEN(HttpStatus.FORBIDDEN, "권한 범위를 넓히는 것은 복약자 본인만 할 수 있습니다."),

    // --- 문자 인증 ---
    LEGAL_REP_PHONE_REQUIRED(HttpStatus.BAD_REQUEST, "만 14세 미만 복약자는 법정대리인 휴대폰 번호가 필요합니다."),
    LEGAL_REP_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "만 14세 미만 복약자는 동의 시 법정대리인 이름이 필요합니다."),
    LEGAL_REP_PHONE_SAME_AS_PATIENT(HttpStatus.BAD_REQUEST, "법정대리인 휴대폰 번호는 복약자 본인 번호와 달라야 합니다."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 인증 요청입니다."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증 시간이 만료되었습니다. 인증번호를 다시 요청해주세요."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증번호 입력 가능 횟수를 초과했습니다. 인증번호를 다시 요청해주세요."),
    VERIFICATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증번호 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "동의하려면 문자 인증이 필요합니다."),
    VERIFICATION_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "문자 인증이 완료되지 않았습니다."),
    VERIFICATION_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, "이 동의에 사용할 수 없는 인증입니다.");

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