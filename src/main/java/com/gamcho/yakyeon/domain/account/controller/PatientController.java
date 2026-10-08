package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.PatientRegisterRequest;
import com.gamcho.yakyeon.domain.account.dto.PatientResponse;
import com.gamcho.yakyeon.domain.account.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-AUTH-009, 011, 018, FR-MULTI-001~004
 *
 * "/patients"는 SecurityConfig의 공개 엔드포인트 목록에 없으므로 별도 설정 없이도
 * 기본적으로 인증이 필요하다 (anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    public ResponseEntity<PatientResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PatientRegisterRequest request) {
        PatientResponse response = patientService.registerPatient(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}