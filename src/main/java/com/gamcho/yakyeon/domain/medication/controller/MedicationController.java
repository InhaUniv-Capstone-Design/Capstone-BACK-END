package com.gamcho.yakyeon.domain.medication.controller;

import com.gamcho.yakyeon.domain.medication.dto.MedicationItem;
import com.gamcho.yakyeon.domain.medication.dto.MedicationResponse;
import com.gamcho.yakyeon.domain.medication.entity.MedicationStatus;
import com.gamcho.yakyeon.domain.medication.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * FR-LIST 약물 목록 조회·수정·삭제. 복약자의 건강정보를 다루므로 모든 경로가 접근 가드를 거친다.
 * GET /medications/autocomplete는 MedicationSearchController에 있고, 구체적인 경로라 이쪽보다 먼저 선택된다.
 */
@RestController
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @GetMapping("/patients/{patientId}/medications")
    public ResponseEntity<List<MedicationResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @RequestParam(value = "status", required = false) MedicationStatus status,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(medicationService.list(userId, patientId, status, date));
    }

    /** 등록 때와 같은 형식으로 약의 전체 내용을 다시 보낸다 (일부 필드만 고치는 방식이 아님) */
    @PatchMapping("/medications/{medicationId}")
    public ResponseEntity<MedicationResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable("medicationId") Long medicationId,
            @Valid @RequestBody MedicationItem request) {
        return ResponseEntity.ok(medicationService.update(userId, medicationId, request));
    }

    @DeleteMapping("/medications/{medicationId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable("medicationId") Long medicationId) {
        medicationService.delete(userId, medicationId);
        return ResponseEntity.noContent().build();
    }
}