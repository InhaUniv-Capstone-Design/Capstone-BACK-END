package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.domain.notification.logic.PushDeliveryRules;
import com.gamcho.yakyeon.domain.notification.push.PushResult;
import com.gamcho.yakyeon.domain.notification.repository.PushDeliveryRepository;
import com.gamcho.yakyeon.domain.notification.repository.PushDeliveryRepository.PendingPush;
import com.gamcho.yakyeon.domain.notification.repository.PushDeliveryRepository.RetryPush;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 발송 결과를 기록한다. 보내는 일(네트워크)이 끝난 뒤에 짧은 트랜잭션으로만 실행해서, 느린 호출 동안 DB 행을
 * 잠그고 있지 않게 한다. 같은 클래스 안의 호출이 아니라 별도 빈이라 트랜잭션이 확실히 적용된다.
 */
@Service
@RequiredArgsConstructor
public class PushDeliveryRecorder {

    private final PushDeliveryRepository pushDeliveryRepository;

    /** 이 기기로 처음 보낸 결과 */
    @Transactional
    public void recordFirstAttempt(PendingPush push, PushResult result, OffsetDateTime now) {
        record(push.notificationId(), push.pushTokenId(), 1, result, now);
    }

    /** 재시도한 결과: 이전 실패 기록의 재시도 표시를 지우고 다음 시도(번호 +1)를 새 행으로 남긴다 */
    @Transactional
    public void recordRetryAttempt(RetryPush retry, PushResult result, OffsetDateTime now) {
        pushDeliveryRepository.clearRetry(retry.deliveryId());
        record(retry.notificationId(), retry.pushTokenId(), retry.attemptNo() + 1, result, now);
    }

    /** 더는 보낼 수 없는 재시도 대상(읽음, 권한 끔, 오래됨, 토큰 삭제, 연동 해제)을 보내지 않고 마감한다 */
    @Transactional
    public void giveUp(long deliveryId) {
        pushDeliveryRepository.clearRetry(deliveryId);
    }

    private void record(long notificationId, Long pushTokenId, int attemptNo, PushResult result, OffsetDateTime now) {
        switch (result.status()) {
            case SENT -> pushDeliveryRepository.insertAttempt(notificationId, pushTokenId, "SENT", attemptNo,
                    result.messageId(), null, null, now);
            case TRANSIENT_FAILURE -> pushDeliveryRepository.insertAttempt(notificationId, pushTokenId, "FAILED", attemptNo,
                    null, result.errorCode(), PushDeliveryRules.nextRetryAt(attemptNo, now).orElse(null), now);
            case INVALID_TOKEN -> {
                // 기록을 먼저 남기고(토큰 번호가 살아 있을 때) 토큰을 지운다. 무효 토큰은 다시 보내지 않는다
                pushDeliveryRepository.insertAttempt(notificationId, pushTokenId, "FAILED", attemptNo,
                        null, result.errorCode(), null, now);
                if (pushTokenId != null) {
                    pushDeliveryRepository.deleteToken(pushTokenId);
                }
            }
        }
    }
}