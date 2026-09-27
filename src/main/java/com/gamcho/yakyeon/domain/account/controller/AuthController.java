package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.CheckIdResponse;
import com.gamcho.yakyeon.domain.account.dto.SignupRequest;
import com.gamcho.yakyeon.domain.account.dto.SignupResponse;
import com.gamcho.yakyeon.domain.account.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API 명세 v0.2 §1 인증/계정
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Long userId = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(userId));
    }

    @GetMapping("/check-id")
    public ResponseEntity<CheckIdResponse> checkId(@RequestParam String loginId) {
        boolean available = authService.isLoginIdAvailable(loginId);
        return ResponseEntity.ok(new CheckIdResponse(available));
    }
}