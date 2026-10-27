package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.notification.dto.NotificationSettingsResponse;
import com.gamcho.yakyeon.domain.notification.dto.NotificationSettingsUpdateRequest;
import com.gamcho.yakyeon.domain.notification.logic.NotificationRules;
import com.gamcho.yakyeon.domain.notification.logic.NotificationSettingKey;
import com.gamcho.yakyeon.domain.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 켜기/끄기 설정 (받는 사람 계정 기준). 설정은 내 알림 수신에만 영향을 주고 다른 사람의 데이터를 건드리지 않아서
 * 본인 계정으로만 읽고 쓴다. 바꾼 적 없는 항목은 켜짐이다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;

    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(Long userId) {
        return toResponse(userId);
    }

    @Transactional
    public NotificationSettingsResponse update(Long userId, NotificationSettingsUpdateRequest request) {
        List<NotificationSettingKey> keys = request.getSettings().stream()
                .map(NotificationSettingsUpdateRequest.Item::getSettingKey).toList();
        if (NotificationRules.hasDuplicateKey(keys)) {
            throw new BusinessException(ErrorCode.INVALID_NOTIFICATION_SETTING, "같은 설정 항목이 두 번 들어 있습니다.");
        }
        for (NotificationSettingsUpdateRequest.Item item : request.getSettings()) {
            notificationSettingRepository.upsert(userId, item.getSettingKey(), item.getEnabled());
        }
        return toResponse(userId);
    }

    private NotificationSettingsResponse toResponse(Long userId) {
        var merged = NotificationRules.withDefaults(
                NotificationRules.parseStored(notificationSettingRepository.findAll(userId)));
        return new NotificationSettingsResponse(merged.entrySet().stream()
                .map(e -> new NotificationSettingsResponse.Item(e.getKey(), e.getValue())).toList());
    }
}