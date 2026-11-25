package com.gamcho.yakyeon.domain.medication.controller;

import com.gamcho.yakyeon.domain.medication.dto.PrescriptionCreateRequest;
import com.gamcho.yakyeon.domain.medication.dto.PrescriptionCreateResponse;
import com.gamcho.yakyeon.domain.medication.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-ADD 처방 등록. 복약자의 건강정보를 쓰는 API라서 접근 가드(PatientAccessGuard)를 거친다.
 * 로그인 필요(SecurityConfig 공개 목록에 없음).
 */
@RestController
@RequestMapping("/patients/{patientId}/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    public ResponseEntity<PrescriptionCreateResponse> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @Valid @RequestBody PrescriptionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prescriptionService.create(userId, patientId, request));
    }
}