package com.gamcho.yakyeon.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 전 컨트롤러 공통 예외 처리.
 * 어느 도메인(account, medication, ...)에서 예외가 나든 항상 같은 포맷으로 응답한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        // message는 ErrorCode의 기본 메시지이거나, 던진 쪽이 지정한 상황별 메시지
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(new ErrorResponse(e.getErrorCode().name(), e.getMessage()));
    }

    /** @Valid 검증 실패(필수값 누락, 형식 오류 등) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    /**
     * 요청 본문(JSON)을 읽지 못한 경우: 문법 오류, 허용되지 않는 enum 값(예: "MORNIG"), 타입 불일치(숫자 자리에 문자) 등.
     * 안 잡으면 스프링 기본 오류 응답이 나가서 공통 에러 포맷이 깨진다.
     * 원문 예외 메시지에는 내부 클래스 이름 등이 들어 있어서 그대로 내려주지 않고 고정 문구만 준다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR", "요청 형식이 올바르지 않습니다. 값의 형식과 허용되는 값을 확인해주세요."));
    }

    /**
     * 쿼리 파라미터·경로 변수의 형식 오류 (예: ?date=abc, ?status=FOO, /medications/abc).
     * 안 잡으면 스프링 기본 오류 응답이 나가 공통 포맷이 깨진다. 입력값은 응답에 되돌려주지 않는다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR", "요청 파라미터 '" + e.getName() + "'의 형식이 올바르지 않습니다."));
    }

    /**
     * 업로드 크기 초과 (spring.servlet.multipart.max-file-size). 컨트롤러에 닿기 전에 던져진다.
     * MultipartException의 하위 타입이라 아래 일반 핸들러보다 이쪽이 먼저 선택된다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.IMAGE_TOO_LARGE.getStatus())
                .body(ErrorResponse.of(ErrorCode.IMAGE_TOO_LARGE));
    }

    /** multipart 요청이 아니거나 형식이 깨진 경우 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR", "파일은 multipart/form-data 형식으로 보내주세요."));
    }

    /** 필수 파일 항목(예: image)이 없음 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR", "필수 항목 '" + e.getRequestPartName() + "'이(가) 없습니다."));
    }

    /** 필수 쿼리 파라미터(예: patient_id)가 없음 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR", "필수 파라미터 '" + e.getParameterName() + "'이(가) 없습니다."));
    }
}