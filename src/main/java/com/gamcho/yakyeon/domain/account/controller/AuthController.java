package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.CheckIdResponse;
import com.gamcho.yakyeon.domain.account.dto.LoginRequest;
import com.gamcho.yakyeon.domain.account.dto.LoginResponse;
import com.gamcho.yakyeon.domain.account.dto.RefreshRequest;
import com.gamcho.yakyeon.domain.account.dto.SignupRequest;
import com.gamcho.yakyeon.domain.account.dto.SignupResponse;
import com.gamcho.yakyeon.domain.account.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API 명세 §1
 *
 * ⚠️ 이 파일은 기존 AuthController에 로그인/리프레시/로그아웃 3개 엔드포인트를 추가한 재구성본입니다.
 * 실제 프로젝트의 signup/check-id 메서드 내용(특히 import, 응답 상태코드)이 이것과 다르면
 * 기존 파일을 기준으로 두고 login/refresh/logout 3개 메서드만 옮겨 붙이세요.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Long userId = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(userId));
    }

    @GetMapping("/check-id")
    public ResponseEntity<CheckIdResponse> checkId(@RequestParam("login_id") String loginId) {
        boolean available = authService.isLoginIdAvailable(loginId);
        return ResponseEntity.ok(new CheckIdResponse(available));
    }

    /** FR-AUTH-005~007: 로그인 */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** FR-AUTH-006: Access Token 재발급 (Refresh Token 회전) */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** FR-AUTH-006: 로그아웃 (해당 Refresh Token만 폐기) */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}