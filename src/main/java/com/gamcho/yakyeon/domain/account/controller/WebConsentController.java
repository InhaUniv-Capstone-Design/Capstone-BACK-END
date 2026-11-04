package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.common.ratelimit.RateLimit;
import com.gamcho.yakyeon.domain.account.dto.GuardianGrantRequest;
import com.gamcho.yakyeon.domain.account.dto.GuardianGrantResponse;
import com.gamcho.yakyeon.domain.account.dto.SharedItemsChangeRequest;
import com.gamcho.yakyeon.domain.account.dto.SharedItemsResponse;
import com.gamcho.yakyeon.domain.account.service.WebConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 복약자 웹 동의(열람 범위). 복약자 웹의 "열람 범위 저장"과, 같은 화면을 다시 열어 저장하는 범위 변경이다.
 * 복약자 계정만 쓸 수 있다.
 */
@RestController
@RequiredArgsConstructor
public class WebConsentController {

    private final WebConsentService webConsentService;

    @RateLimit(name = "guardian-grant", limit = 30, windowSeconds = 3600)
    @PostMapping("/guardian-links/grant")
    public ResponseEntity<GuardianGrantResponse> grant(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GuardianGrantRequest request) {
        return ResponseEntity.ok(webConsentService.grant(userId, request));
    }

    @PatchMapping("/guardian-links/{linkId}/shared-items")
    public ResponseEntity<SharedItemsResponse> changeSharedItems(
            @AuthenticationPrincipal Long userId,
            @PathVariable("linkId") Long linkId,
            @Valid @RequestBody SharedItemsChangeRequest request) {
        return ResponseEntity.ok(webConsentService.changeSharedItems(userId, linkId, request));
    }
}