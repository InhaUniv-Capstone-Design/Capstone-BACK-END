package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.ProfileResponse;
import com.gamcho.yakyeon.domain.account.dto.ProfileUpdateRequest;
import com.gamcho.yakyeon.domain.account.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 내 프로필 조회·수정 (설정 > 프로필). 로그인한 본인만 다룬다. */
@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/users/me/profile")
    public ResponseEntity<ProfileResponse> get(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(profileService.get(userId));
    }

    /** 이름만 수정할 수 있다. 다른 항목을 보내면 400이다 */
    @PatchMapping("/users/me/profile")
    public ResponseEntity<ProfileResponse> update(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateName(userId, request));
    }
}