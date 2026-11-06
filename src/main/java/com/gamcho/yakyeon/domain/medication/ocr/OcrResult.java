package com.gamcho.yakyeon.domain.medication.ocr;

import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * OCR 엔진이 처방전 이미지에서 읽어낸 결과 (엔진이 무엇이든 같은 모양).
 * 읽어낸 약 이름은 글자 그대로의 원문이라 약물 DB와 매칭되지 않을 수 있다 - 매칭은 서비스가 맡는다.
 * 읽지 못한 항목은 null이다.
 */
public record OcrResult(
        String hospitalName,
        String pharmacyName,
        LocalDate prescribedDate,
        List<Item> items
) {

    /** 처방전에서 읽어낸 약 한 줄 */
    public record Item(
            String drugName,
            BigDecimal dosePerIntake,
            Short timesPerDay,
            Integer totalDays,
            boolean prn,
            PatientMedication.MealRelation mealRelation,
            Short mealOffsetMin,
            List<String> timingSlots
    ) {

        /**
         * 복용 시간대(MORNING, LUNCH, DINNER, BEDTIME)는 처방전에 아침·점심·저녁 칸이 있는 양식에서만 읽을 수 있다.
         * 읽지 못했으면 빈 목록이고, 프론트가 사용자에게 고르게 한다.
         */
        public Item {
            timingSlots = timingSlots == null ? List.of() : List.copyOf(timingSlots);
        }

        /** 복용 시간대를 읽지 못한 경우(시간대 칸이 없는 양식) */
        public Item(String drugName, BigDecimal dosePerIntake, Short timesPerDay, Integer totalDays, boolean prn,
                    PatientMedication.MealRelation mealRelation, Short mealOffsetMin) {
            this(drugName, dosePerIntake, timesPerDay, totalDays, prn, mealRelation, mealOffsetMin, List.of());
        }
    }
}