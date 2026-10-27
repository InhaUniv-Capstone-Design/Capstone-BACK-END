package com.gamcho.yakyeon.domain.device.controller;

import com.gamcho.yakyeon.domain.device.dto.DeviceRegisterRequest;
import com.gamcho.yakyeon.domain.device.dto.DeviceResponse;
import com.gamcho.yakyeon.domain.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱의 약통 등록·조회·해제 (약통 연결 화면). 복약자 한 명에게 약통 하나만 등록된다.
 * 등록과 해제는 수정 권한(본인 또는 읽기·쓰기 권한 보호자), 조회는 조회 권한이 필요하다.
 */
@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/patients/{patientId}/device")
    public ResponseEntity<DeviceResponse> register(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @Valid @RequestBody DeviceRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.register(userId, patientId, request));
    }

    @GetMapping("/patients/{patientId}/device")
    public ResponseEntity<DeviceResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.ok(deviceService.get(userId, patientId));
    }

    @DeleteMapping("/patients/{patientId}/device")
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        deviceService.unregister(userId, patientId);
        return ResponseEntity.noContent().build();
    }
}