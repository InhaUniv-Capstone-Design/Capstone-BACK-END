package com.gamcho.yakyeon.domain.medication.controller;

import com.gamcho.yakyeon.domain.medication.dto.HospitalGroupResponse;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionResponse;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.service.PrescriptionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * FR-LIST-006 처방·병원별 약 조회. 복약자의 건강정보를 다루므로 접근 가드를 거친다 (조회 권한).
 * 같은 경로의 POST(처방 등록)는 PrescriptionController에 있다.
 *
 *  - groupByHospital 없음/false : 처방 단위 목록 [{prescription_id, hospital_name, pharmacy_name, prescribed_date, medications}]
 *  - groupByHospital=true       : 병원별 묶음 [{hospital_name, medications}]
 *  - status, date               : 약 목록 조회와 같은 의미 (기준일에 해당 상태인 약만)
 */
@RestController
@RequiredArgsConstructor
public class PrescriptionQueryController {

    private final PrescriptionQueryService prescriptionQueryService;

    @GetMapping("/patients/{patientId}/prescriptions")
    public ResponseEntity<?> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @RequestParam(value = "groupByHospital", defaultValue = "false") boolean groupByHospital,
            @RequestParam(value = "status", required = false) MedicationStatus status,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (groupByHospital) {
            return ResponseEntity.ok(prescriptionQueryService.listByHospital(userId, patientId, status, date));
        }
        return ResponseEntity.ok(prescriptionQueryService.listPrescriptions(userId, patientId, status, date));
    }
}