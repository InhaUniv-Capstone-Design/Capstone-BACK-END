package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.AccountDeleteRequest;
import com.gamcho.yakyeon.domain.account.dto.CheckIdResponse;
import com.gamcho.yakyeon.domain.account.dto.LoginRequest;
import com.gamcho.yakyeon.domain.account.dto.LoginResponse;
import com.gamcho.yakyeon.domain.account.dto.PasswordChangeRequest;
import com.gamcho.yakyeon.domain.account.dto.RefreshRequest;
import com.gamcho.yakyeon.domain.account.dto.SignupRequest;
import com.gamcho.yakyeon.domain.account.dto.SignupResponse;
import com.gamcho.yakyeon.domain.account.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST API 명세 §1
 * changePassword/deleteAccount는 @AuthenticationPrincipal Long userId로 로그인한
 * 사용자의 ID를 받는다 - JwtAuthenticationFilter가 SecurityContext에 세팅해준 값이다.
 * SecurityConfig에서 이 두 경로는 "/auth/**" permitAll 범위에서 빠져있어야
 * 인증 없이 호출되는 걸 막을 수 있다 (SecurityConfig도 같이 갱신했으니 확인할 것).
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

    /** 비밀번호 변경 - 로그인 필요 */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    /** 계정 삭제(탈퇴) - 로그인 필요, 비밀번호 재확인 */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AccountDeleteRequest request) {
        authService.deleteAccount(userId, request);
        return ResponseEntity.noContent().build();
    }
}