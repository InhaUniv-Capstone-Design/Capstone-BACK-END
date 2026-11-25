package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.ManagedPatientResponse;
import com.gamcho.yakyeon.domain.account.service.ManagedPatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 내가 볼 수 있는 복약자 목록 (보호자의 복약자 선택·전환 화면). */
@RestController
@RequiredArgsConstructor
public class ManagedPatientController {

    private final ManagedPatientService managedPatientService;

    @GetMapping("/users/me/managed-patients")
    public ResponseEntity<List<ManagedPatientResponse>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(managedPatientService.list(userId));
    }
}