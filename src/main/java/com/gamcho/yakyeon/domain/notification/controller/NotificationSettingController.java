package com.gamcho.yakyeon.domain.notification.controller;

import com.gamcho.yakyeon.domain.notification.dto.NotificationSettingsResponse;
import com.gamcho.yakyeon.domain.notification.dto.NotificationSettingsUpdateRequest;
import com.gamcho.yakyeon.domain.notification.service.NotificationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 알림 켜기/끄기 설정 ("알림 설정"과 "보호자 공유 알림" 화면). 받는 사람 본인 계정 기준이다 */
@RestController
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @GetMapping("/users/me/notification-settings")
    public ResponseEntity<NotificationSettingsResponse> get(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(notificationSettingService.get(userId));
    }

    /** 바꾸려는 항목만 보내면 된다. 바뀐 뒤의 8개 전체를 돌려준다 */
    @PatchMapping("/users/me/notification-settings")
    public ResponseEntity<NotificationSettingsResponse> update(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody NotificationSettingsUpdateRequest request) {
        return ResponseEntity.ok(notificationSettingService.update(userId, request));
    }
}