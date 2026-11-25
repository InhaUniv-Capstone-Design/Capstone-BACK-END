package com.gamcho.yakyeon.domain.medication.ocr;

/**
 * 처방전 인식 엔진을 지금 쓸 수 없을 때(연결 실패, 시간 초과, 호출 한도, 엔진 오류, 알 수 없는 응답) 던진다.
 * 서비스가 503 OCR_UNAVAILABLE로 바꿔 사용자에게 알린다. reason은 원인만 담는다(예: "status=401,code=0002").
 * 이미지 내용, 비밀 키, 응답 본문은 절대 담지 않는다 - 이 값은 로그에 남는다.
 */
public class OcrUnavailableException extends RuntimeException {

    private final String reason;

    public OcrUnavailableException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}