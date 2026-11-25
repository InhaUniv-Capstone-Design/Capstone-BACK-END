package com.gamcho.yakyeon.domain.medication.ocr.clova;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.medication.ocr.ImageSignature;
import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;
import com.gamcho.yakyeon.domain.medication.ocr.PrescriptionOcrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 처방전 이미지를 CLOVA OCR로 읽는다: 이미지 전송(ClovaOcrHttpClient) → 응답 해석(ClovaOcrResponseParser) →
 * 처방전 내용 추출(PrescriptionTextParser). 이미지는 메모리에서만 쓰고 어디에도 저장하지 않는다.
 *
 * 이 클래스는 설정 ocr.provider=clova 일 때만 ClovaOcrConfig가 만든다. 엔진을 쓸 수 없으면 OcrUnavailableException을 던지고,
 * 서비스가 503 OCR_UNAVAILABLE로 바꾼다.
 */
public class ClovaPrescriptionOcrClient implements PrescriptionOcrClient {

    private static final Logger log = LoggerFactory.getLogger(ClovaPrescriptionOcrClient.class);

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
        long started = System.nanoTime();
        String json = http.recognize(image, type == ImageSignature.Type.JPEG ? "jpg" : "png");
        long httpMs = (System.nanoTime() - started) / 1_000_000;
        OcrDocument document = ClovaOcrResponseParser.parse(json);
        OcrResult result = PrescriptionTextParser.parse(document, ApiTime.today());
        // 처방전이 안 읽히는 이유를 로그만으로 가를 수 있게 개수와 시간만 남긴다 (글자 내용, 이름, 번호는 남기지 않는다):
        // 글자 조각이 0이면 CLOVA가 글자를 못 읽은 것, 글자는 많은데 약이 0이면 처방전 양식이 해석 규칙과 다른 것이다.
        log.info("CLOVA 인식: type={}, bytes={}, clovaMs={}, 응답바이트={}, 글자조각={}, 줄={}, 표={}, 약={}, 병원={}, 처방일={}",
                type, image.length, httpMs, json.length(), document.words().size(), document.lines().size(), document.tables().size(),
                result.items().size(), result.hospitalName() != null, result.prescribedDate() != null);
        if (document.isEmpty()) {
            log.warn("CLOVA가 글자를 읽지 못했습니다 (사진이 흐리거나 처방전이 아닐 수 있음)");
        } else if (result.items().isEmpty()) {
            log.warn("글자는 읽었지만 약 목록을 만들지 못했습니다: 글자조각={}, 줄={}, 표={} (처방전 양식이 해석 규칙과 다를 수 있음)",
                    document.words().size(), document.lines().size(), document.tables().size());
        }
        return result;
    }
}