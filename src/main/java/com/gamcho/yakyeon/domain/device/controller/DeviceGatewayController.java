package com.gamcho.yakyeon.domain.device.controller;

import com.gamcho.yakyeon.domain.device.dto.DeviceHeartbeatRequest;
import com.gamcho.yakyeon.domain.device.dto.DeviceHeartbeatResponse;
import com.gamcho.yakyeon.domain.device.service.DeviceStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약통(기기)이 서버를 부르는 경로. 사람의 로그인 토큰이 없으므로 JWT가 아니라 헤더의
 * 시리얼번호와 비밀값으로 인증한다 (SecurityConfig에서 이 경로만 공개로 열고 서비스에서 인증한다).
 * 헤더가 없어도 기본 오류가 아니라 인증 실패와 똑같은 응답이 나가도록 헤더를 선택으로 받는다.
 */
@RestController
@RequiredArgsConstructor
public class DeviceGatewayController {

    private final DeviceStatusService deviceStatusService;

    /** 상태 보고. 서버 시각과 최신 일정 버전을 돌려준다 */
    @PostMapping("/device/heartbeat")
    public ResponseEntity<DeviceHeartbeatResponse> heartbeat(
            @RequestHeader(value = "X-Device-Serial", required = false) String serialNo,
            @RequestHeader(value = "X-Device-Secret", required = false) String secret,
            @Valid @RequestBody(required = false) DeviceHeartbeatRequest request) {
        return ResponseEntity.ok(deviceStatusService.heartbeat(serialNo, secret, request));
    }
}