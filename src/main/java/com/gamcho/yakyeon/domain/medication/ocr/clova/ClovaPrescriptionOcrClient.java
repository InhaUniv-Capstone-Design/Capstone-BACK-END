package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.medication.ocr.ImageSignature;
import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;
import com.gamcho.yakyeon.domain.medication.ocr.PrescriptionOcrClient;

/**
 * 처방전 이미지를 CLOVA OCR로 읽는다: 이미지 전송(ClovaOcrHttpClient) → 응답 해석(ClovaOcrResponseParser) →
 * 처방전 내용 추출(PrescriptionTextParser). 이미지는 메모리에서만 쓰고 어디에도 저장하지 않는다.
 *
 * 이 클래스는 설정 ocr.provider=clova 일 때만 ClovaOcrConfig가 만든다. 엔진을 쓸 수 없으면 OcrUnavailableException을 던지고,
 * 서비스가 503 OCR_UNAVAILABLE로 바꾼다.
 */
public class ClovaPrescriptionOcrClient implements PrescriptionOcrClient {

    private final ClovaOcrHttpClient http;

    public ClovaPrescriptionOcrClient(ClovaOcrHttpClient http) {
        this.http = http;
    }

    @Override
    public OcrResult recognize(byte[] image) {
        ImageSignature.Type type = ImageSignature.detect(image);
        if (type == null) {
            throw new IllegalArgumentException("JPEG 또는 PNG 이미지여야 합니다.");   // 서비스가 이미 확인해서 넘기므로 여기까지 오지 않는다
        }
        String json = http.recognize(image, type == ImageSignature.Type.JPEG ? "jpg" : "png");
        return PrescriptionTextParser.parse(ClovaOcrResponseParser.parse(json), ApiTime.today());
    }
}