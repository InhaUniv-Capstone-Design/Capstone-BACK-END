package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.common.ratelimit.RateLimit;
import com.gamcho.yakyeon.domain.account.dto.FindIdRequest;
import com.gamcho.yakyeon.domain.account.dto.FindIdResponse;
import com.gamcho.yakyeon.domain.account.dto.PasswordResetRequest;
import com.gamcho.yakyeon.domain.account.service.AccountRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아이디 찾기·비밀번호 재설정 (로그인 없이 호출, SecurityConfig에서 공개).
 *
 * 흐름은 가입 때와 같다: POST /auth/phone-verifications → (문자) → POST /auth/phone-verifications/{id}/confirm 으로
 * 증표(verification_token)를 받은 뒤, 그 증표를 이 API들에 같이 보낸다. 인증을 먼저 하는 이유는 PasswordResetRequest 참고.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AccountRecoveryController {

    private final AccountRecoveryService accountRecoveryService;

    @RateLimit(name = "find-id", limit = 60, windowSeconds = 3600)
    @PostMapping("/find-id")
    public FindIdResponse findId(@Valid @RequestBody FindIdRequest request) {
        return accountRecoveryService.findId(request);
    }

    @RateLimit(name = "password-reset", limit = 60, windowSeconds = 3600)
    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        accountRecoveryService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}