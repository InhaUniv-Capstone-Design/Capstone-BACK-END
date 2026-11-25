package com.gamcho.yakyeon.domain.notification.controller;

import com.gamcho.yakyeon.domain.notification.dto.PushTokenDeleteRequest;
import com.gamcho.yakyeon.domain.notification.dto.PushTokenRequest;
import com.gamcho.yakyeon.domain.notification.service.PushTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 푸시 알림용 FCM 토큰 등록·해제. 토큰은 주소가 아니라 본문으로만 주고받는다 */
@RestController
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    /** 로그인 직후와 토큰이 새로 발급될 때마다 호출. 이미 있는 토큰이면 지금 계정의 것으로 갱신한다 */
    @PutMapping("/users/me/push-token")
    public ResponseEntity<Void> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PushTokenRequest request) {
        pushTokenService.register(userId, request);
        return ResponseEntity.noContent().build();
    }

    /** 로그아웃할 때 이 기기의 토큰을 해제 */
    @DeleteMapping("/users/me/push-token")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PushTokenDeleteRequest request) {
        pushTokenService.remove(userId, request);
        return ResponseEntity.noContent().build();
    }
}