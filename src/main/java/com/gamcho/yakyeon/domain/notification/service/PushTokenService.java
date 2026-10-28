package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.domain.notification.dto.PushTokenDeleteRequest;
import com.gamcho.yakyeon.domain.notification.dto.PushTokenRequest;
import com.gamcho.yakyeon.domain.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 푸시 알림용 FCM 토큰 등록·해제 (본인 계정 기준). 토큰은 로그에 남기지 않는다 */
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;

    @Transactional
    public void register(Long userId, PushTokenRequest request) {
        boolean permitted = request.getNotificationPermitted() == null || request.getNotificationPermitted();
        pushTokenRepository.upsert(userId, request.getFcmToken(), permitted);
    }

    @Transactional
    public void remove(Long userId, PushTokenDeleteRequest request) {
        pushTokenRepository.delete(userId, request.getFcmToken());
    }
}