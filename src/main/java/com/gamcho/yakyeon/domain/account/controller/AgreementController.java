package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.consent.ConsentExempt;
import com.gamcho.yakyeon.domain.account.dto.AgreementRequest;
import com.gamcho.yakyeon.domain.account.dto.AgreementResponse;
import com.gamcho.yakyeon.domain.account.service.AgreementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 약관 동의 관리 (설정 > 서비스 안내 및 정보 동의). 필수 동의를 철회한 사용자도 다시 동의할 수 있어야 해서
 * 이 컨트롤러 전체가 필수 동의 확인에서 제외된다(@ConsentExempt).
 */
@RestController
@RequestMapping("/users/me/agreements")
@ConsentExempt
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService agreementService;

    @GetMapping
    public ResponseEntity<List<AgreementResponse>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(agreementService.list(userId));
    }

    @PostMapping
    public ResponseEntity<AgreementResponse> agree(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AgreementRequest request) {
        return ResponseEntity.ok(agreementService.agree(userId, request));
    }

    @PostMapping("/{termsType}/withdraw")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Long userId,
            @PathVariable("termsType") String termsType) {
        agreementService.withdraw(userId, termsType);
        return ResponseEntity.noContent().build();
    }
}