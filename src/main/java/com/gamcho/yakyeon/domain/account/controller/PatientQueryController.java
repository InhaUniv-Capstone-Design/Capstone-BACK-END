package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.PatientDetailResponse;
import com.gamcho.yakyeon.domain.account.service.PatientQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 복약자 기본 정보 조회. 로그인 필요(SecurityConfig 공개 목록에 없음).
 * GET /patients/search는 더 구체적인 경로라 이 {patientId} 매핑보다 먼저 선택된다.
 */
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientQueryController {

    private final PatientQueryService patientQueryService;

    @GetMapping("/{patientId}")
    public ResponseEntity<PatientDetailResponse> getPatient(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.ok(patientQueryService.getPatient(userId, patientId));
    }
}