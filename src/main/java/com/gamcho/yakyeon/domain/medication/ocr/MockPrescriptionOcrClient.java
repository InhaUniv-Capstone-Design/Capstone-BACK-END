package com.gamcho.yakyeon.domain.medication.ocr;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 개발용 Mock OCR. 이미지 내용과 상관없이 항상 같은 결과를 돌려준다 (이미지는 읽지도 저장하지도 않는다).
 *
 * 설정에 ocr.provider: mock 이 "명시적으로" 있을 때만 켜진다. 설정이 없으면 이 빈이 만들어지지 않아
 * 앱이 시작되지 않는다(PrescriptionOcrClient 빈 없음). SMS Mock은 설정이 없어도 켜져서 배포 서버에서
 * 설정을 빠뜨리면 조용히 가짜로 동작하는 문제가 있었는데, 그 실수를 반복하지 않으려는 의도다.
 *
 * 세 가지 매칭 결과를 모두 볼 수 있게 약을 골랐다 (임시 약물 데이터 기준).
 *  1) 같은 이름의 제품이 업체별로 둘 이상  → 자동 매칭 불가, 후보 여러 개 (사용자가 선택)
 *  2) 이름이 정확히 하나                    → 자동 매칭
 *  3) 약물 DB에 없음                        → 매칭 불가, 후보 없음 ("확인할 수 없음")
 */
@Component
@ConditionalOnProperty(name = "ocr.provider", havingValue = "mock")
public class MockPrescriptionOcrClient implements PrescriptionOcrClient {

    @Override
    public OcrResult recognize(byte[] image) {
        return new OcrResult(
                "Seoul Hospital",
                "Central Pharmacy",
                ApiTime.today(),
                List.of(
                        new OcrResult.Item("메트포르민정500mg", BigDecimal.ONE, (short) 2, 30, false,
                                PatientMedication.MealRelation.AFTER_MEAL, (short) 30),
                        new OcrResult.Item("아토르바스타틴정10mg", BigDecimal.ONE, (short) 1, 30, false,
                                null, null),
                        new OcrResult.Item("소화제", null, null, 7, true, null, null)));
    }
}