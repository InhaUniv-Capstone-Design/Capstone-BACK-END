package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.ConsentHistoryResponse;
import com.gamcho.yakyeon.domain.account.service.ConsentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FR-MY-010: 본인 동의 이력 조회. 로그인 필요(SecurityConfig 공개 목록에 없음).
 */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class ConsentHistoryController {

    private final ConsentHistoryService consentHistoryService;

    @GetMapping("/consents")
    public ResponseEntity<List<ConsentHistoryResponse>> getMyConsents(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(consentHistoryService.getMyHistory(userId, page, size));
    }
}