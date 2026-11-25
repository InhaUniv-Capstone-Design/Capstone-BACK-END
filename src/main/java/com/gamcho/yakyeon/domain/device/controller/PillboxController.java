package com.gamcho.yakyeon.domain.device.controller;

import com.gamcho.yakyeon.domain.device.dto.PillboxResponse;
import com.gamcho.yakyeon.domain.device.service.PillboxQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 약통 화면: 칸별 약봉지와 남은 개수. 조회 권한이 필요하다 (본인 또는 연동된 보호자) */
@RestController
@RequiredArgsConstructor
public class PillboxController {

    private final PillboxQueryService pillboxQueryService;

    @GetMapping("/patients/{patientId}/pillbox")
    public ResponseEntity<PillboxResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.ok(pillboxQueryService.get(userId, patientId));
    }
}