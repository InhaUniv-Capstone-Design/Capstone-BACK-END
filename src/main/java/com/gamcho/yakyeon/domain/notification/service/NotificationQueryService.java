package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.notification.dto.NotificationListResponse;
import com.gamcho.yakyeon.domain.notification.dto.ReadAllResponse;
import com.gamcho.yakyeon.domain.notification.dto.UnreadCountResponse;
import com.gamcho.yakyeon.domain.notification.logic.NotificationRules;
import com.gamcho.yakyeon.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 내 알림 목록, 안 읽은 수, 읽음 처리. 알림은 받는 사람 본인만 볼 수 있고,
 * 번호만으로 남의 알림이 존재하는지 알 수 없도록 "없는 번호"와 같은 오류를 준다.
 * 복약자 정보 자체를 조회하는 API가 아니라서 접근 가드(PatientAccessGuard)는 거치지 않지만,
 * 복약자 정보를 더는 볼 수 없는 알림(해제된 보호자의 알림)은 목록에서 빠진다 (NotificationRepository 참고).
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, boolean unreadOnly, int page, int size) {
        if (!NotificationRules.isValidPaging(page, size)) {
            throw new BusinessException(ErrorCode.INVALID_QUERY, "페이지는 0~" + NotificationRules.MAX_PAGE
                    + ", 크기는 1~" + NotificationRules.MAX_PAGE_SIZE + "이어야 합니다.");
        }
        return new NotificationListResponse(page, size, notificationRepository.count(userId, unreadOnly),
                notificationRepository.count(userId, true),
                notificationRepository.list(userId, unreadOnly, size, page * size));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.count(userId, true));
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        if (!notificationRepository.markRead(userId, notificationId, OffsetDateTime.now(ApiTime.SERVICE_ZONE))) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Transactional
    public ReadAllResponse markAllRead(Long userId) {
        return new ReadAllResponse(notificationRepository.markAllRead(userId, OffsetDateTime.now(ApiTime.SERVICE_ZONE)));
    }
}