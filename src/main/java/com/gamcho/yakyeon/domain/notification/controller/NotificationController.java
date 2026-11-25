package com.gamcho.yakyeon.domain.notification.controller;

import com.gamcho.yakyeon.domain.notification.dto.NotificationListResponse;
import com.gamcho.yakyeon.domain.notification.dto.ReadAllResponse;
import com.gamcho.yakyeon.domain.notification.dto.UnreadCountResponse;
import com.gamcho.yakyeon.domain.notification.logic.NotificationRules;
import com.gamcho.yakyeon.domain.notification.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 알림: 목록, 안 읽은 수, 읽음 처리 (알림 화면, 종 모양 아이콘의 배지) */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    /** 최신순. unread_only=true면 읽지 않은 알림만. page는 0부터, size는 1~100 (기본 20) */
    @GetMapping("/users/me/notifications")
    public ResponseEntity<NotificationListResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "unread_only", defaultValue = "false") boolean unreadOnly,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + NotificationRules.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(notificationQueryService.list(userId, unreadOnly, page, size));
    }

    @GetMapping("/users/me/notifications/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(notificationQueryService.unreadCount(userId));
    }

    /** 알림 하나를 읽음 처리. 이미 읽은 알림이어도 같은 결과다 */
    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable("notificationId") Long notificationId) {
        notificationQueryService.markRead(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/me/notifications/read-all")
    public ResponseEntity<ReadAllResponse> markAllRead(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(notificationQueryService.markAllRead(userId));
    }
}