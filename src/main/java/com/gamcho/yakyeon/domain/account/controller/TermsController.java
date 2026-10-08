package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.TermsResponse;
import com.gamcho.yakyeon.domain.account.service.TermsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FR-AUTH-008, 013, 016, 017
 * 인증 불필요 - 회원가입 화면에서 로그인 전에 호출한다 (SecurityConfig 공개 목록에 등록됨).
 */
@RestController
@RequestMapping("/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @GetMapping
    public ResponseEntity<List<TermsResponse>> getCurrentTerms() {
        return ResponseEntity.ok(termsService.getCurrentTerms());
    }
}