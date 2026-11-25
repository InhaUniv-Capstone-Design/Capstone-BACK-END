package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.GuardianLinkItemResponse;
import com.gamcho.yakyeon.domain.account.service.GuardianLinkQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 설정 > 보호자 연동: 복약자 본인이 자기에게 연동된 보호자 목록을 본다. 같은 경로의 POST(연동 요청)는 GuardianLinkController에 있다. */
@RestController
@RequiredArgsConstructor
public class GuardianLinkListController {

    private final GuardianLinkQueryService guardianLinkQueryService;

    /** status 생략하면 ACTIVE와 PENDING. PENDING, ACTIVE, REJECTED, REVOKED 중 하나로 거를 수 있다 */
    @GetMapping("/patients/{patientId}/guardian-links")
    public ResponseEntity<List<GuardianLinkItemResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(guardianLinkQueryService.list(userId, patientId, status));
    }
}