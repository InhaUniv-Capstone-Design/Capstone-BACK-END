package com.gamcho.yakyeon.domain.medication.controller;

import com.gamcho.yakyeon.domain.medication.dto.MedicationResponse;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionEndResponse;
import com.gamcho.yakyeon.domain.medication.service.MedicationEndService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 복용 종료 (약 팝업 "복용을 종료하시겠습니까?"). 삭제(DELETE)와 달리 약이 "종료됨"으로 남고 복용 기록은 보관된다.
 * 수정 권한이 필요하다 (본인 또는 읽기·쓰기 권한 보호자).
 */
@RestController
@RequiredArgsConstructor
public class MedicationEndController {

    private final MedicationEndService medicationEndService;

    /** 약 한 건 종료. 응답은 종료된 약(status=ENDED, ended_on=종료한 날) */
    @PostMapping("/medications/{medicationId}/end")
    public ResponseEntity<MedicationResponse> endMedication(
            @AuthenticationPrincipal Long userId,
            @PathVariable("medicationId") Long medicationId) {
        return ResponseEntity.ok(medicationEndService.endMedication(userId, medicationId));
    }

    /** 처방(약봉지) 전체 종료. 배정된 약통 칸도 함께 비워진다 */
    @PostMapping("/prescriptions/{prescriptionId}/end")
    public ResponseEntity<PrescriptionEndResponse> endPrescription(
            @AuthenticationPrincipal Long userId,
            @PathVariable("prescriptionId") Long prescriptionId) {
        return ResponseEntity.ok(medicationEndService.endPrescription(userId, prescriptionId));
    }
}