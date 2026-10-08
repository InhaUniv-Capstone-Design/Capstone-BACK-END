package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.RevocationConfirmRequest;
import com.gamcho.yakyeon.domain.account.dto.RevocationConfirmResponse;
import com.gamcho.yakyeon.domain.account.dto.RevocationRequestBody;
import com.gamcho.yakyeon.domain.account.dto.RevocationRequestResponse;
import com.gamcho.yakyeon.domain.account.service.RevocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문자 인증 기반 동의 철회 (FR-AUTH-014) - 앱 계정이 없는 복약자용.
 * 로그인할 수 없는 사람이 쓰는 API라서 SecurityConfig에서 인증 없이 열려 있다.
 * (그만큼 응답으로 회원 여부가 드러나지 않게 RevocationService에서 막아둔 상태)
 */
@RestController
@RequestMapping("/revocations")
@RequiredArgsConstructor
public class RevocationController {

    private final RevocationService revocationService;

    @PostMapping("/request")
    public ResponseEntity<RevocationRequestResponse> request(@Valid @RequestBody RevocationRequestBody body) {
        return ResponseEntity.ok(new RevocationRequestResponse(revocationService.requestCode(body.getPhone())));
    }

    @PostMapping("/confirm")
    public ResponseEntity<RevocationConfirmResponse> confirm(@Valid @RequestBody RevocationConfirmRequest body) {
        int count = revocationService.confirm(body.getPhone(), body.getCode());
        return ResponseEntity.ok(new RevocationConfirmResponse(count > 0, count));
    }
}