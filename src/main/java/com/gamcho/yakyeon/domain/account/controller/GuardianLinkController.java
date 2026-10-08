package com.gamcho.yakyeon.domain.account.controller;

import com.gamcho.yakyeon.domain.account.dto.ConsentRequest;
import com.gamcho.yakyeon.domain.account.dto.ConsentResponse;
import com.gamcho.yakyeon.domain.account.dto.GuardianLinkResponse;
import com.gamcho.yakyeon.domain.account.dto.PatientSearchResponse;
import com.gamcho.yakyeon.domain.account.dto.PermissionChangeRequest;
import com.gamcho.yakyeon.domain.account.dto.PermissionChangeResponse;
import com.gamcho.yakyeon.domain.account.dto.VerificationConfirmRequest;
import com.gamcho.yakyeon.domain.account.dto.VerificationConfirmResponse;
import com.gamcho.yakyeon.domain.account.dto.VerificationSendRequest;
import com.gamcho.yakyeon.domain.account.dto.VerificationSendResponse;
import com.gamcho.yakyeon.domain.account.service.GuardianLinkService;
import com.gamcho.yakyeon.domain.account.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 보호자 대리 동의 흐름 (FR-AUTH-009~015)
 * 복약자 검색 → 연동 요청 생성 → 인증 문자 발송 → 인증번호 확인 → 동의/거부
 * 철회·연동 해제(DELETE)는 보호자뿐 아니라 앱 계정이 있는 복약자 본인도 호출할 수 있다.
 *
 * 전부 SecurityConfig 공개 목록에 없으므로 로그인이 필요하다 (철회를 뺀 나머지는 보호자 전용).
 */
@RestController
@RequiredArgsConstructor
public class GuardianLinkController {

    private final GuardianLinkService guardianLinkService;
    private final VerificationService verificationService;

    @GetMapping("/patients/search")
    public ResponseEntity<List<PatientSearchResponse>> searchPatients(
            @AuthenticationPrincipal Long userId,
            @RequestParam("phone") String phone) {
        return ResponseEntity.ok(guardianLinkService.searchPatients(userId, phone));
    }

    @PostMapping("/patients/{patientId}/guardian-links")
    public ResponseEntity<GuardianLinkResponse> createLink(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guardianLinkService.createLink(userId, patientId));
    }

    /** 요청 바디는 만 14세 미만(법정대리인 번호 필요)일 때만 필요하다 */
    @PostMapping("/guardian-links/{linkId}/verification")
    public ResponseEntity<VerificationSendResponse> sendVerification(
            @AuthenticationPrincipal Long userId,
            @PathVariable("linkId") Long linkId,
            @RequestBody(required = false) VerificationSendRequest request) {
        return ResponseEntity.ok(verificationService.send(userId, linkId, request));
    }

    @PostMapping("/verifications/{verificationId}/confirm")
    public ResponseEntity<VerificationConfirmResponse> confirmVerification(
            @AuthenticationPrincipal Long userId,
            @PathVariable("verificationId") Long verificationId,
            @Valid @RequestBody VerificationConfirmRequest request) {
        boolean verified = verificationService.confirm(userId, verificationId, request.getCode());
        return ResponseEntity.ok(new VerificationConfirmResponse(verified));
    }

    @PostMapping("/guardian-links/{linkId}/consent")
    public ResponseEntity<ConsentResponse> consent(
            @AuthenticationPrincipal Long userId,
            @PathVariable("linkId") Long linkId,
            @Valid @RequestBody ConsentRequest request) {
        return ResponseEntity.ok(guardianLinkService.consent(userId, linkId, request));
    }

    /** 철회·연동 해제 - 연동의 보호자 본인 또는 앱 계정이 있는 복약자 본인만 가능 */
    @DeleteMapping("/guardian-links/{linkId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal Long userId,
            @PathVariable("linkId") Long linkId) {
        guardianLinkService.revoke(userId, linkId);
        return ResponseEntity.noContent().build();
    }

    /** 보호자 권한 범위 변경 - 복약자 본인은 올리고 내리기, 보호자는 낮추기만 가능 */
    @PatchMapping("/guardian-links/{linkId}/permission")
    public ResponseEntity<PermissionChangeResponse> changePermission(
            @AuthenticationPrincipal Long userId,
            @PathVariable("linkId") Long linkId,
            @Valid @RequestBody PermissionChangeRequest request) {
        return ResponseEntity.ok(
                guardianLinkService.changePermission(userId, linkId, request.getPermissionScope()));
    }
}