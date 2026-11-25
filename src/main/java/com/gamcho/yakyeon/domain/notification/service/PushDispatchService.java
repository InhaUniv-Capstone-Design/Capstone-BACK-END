package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.notification.logic.PushDeliveryRules;
import com.gamcho.yakyeon.domain.notification.push.PushMessage;
import com.gamcho.yakyeon.domain.notification.push.PushResult;
import com.gamcho.yakyeon.domain.notification.push.PushSender;
import com.gamcho.yakyeon.domain.notification.repository.PushDeliveryRepository;
import com.gamcho.yakyeon.domain.notification.repository.PushDeliveryRepository.PendingPush;
import com.gamcho.yakyeon.domain.notification.repository.PushDeliveryRepository.RetryPush;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 저장된 알림을 푸시로 보낸다. 이 클래스는 일부러 @Transactional이 없다:
 * 읽기(대상 찾기) → 보내기(PushSender, 네트워크) → 기록(PushDeliveryRecorder, 짧은 트랜잭션)을 각각 따로 해서
 * 느린 발송이 DB 행을 잠그고 있지 않게 한다. 보낸 뒤 서버가 멈추면 같은 푸시가 한 번 더 갈 수 있다(최소 한 번 전달).
 * 한 건이 실패해도 나머지는 계속 보낸다. 로그에는 건수와 결과 종류만 남기고 토큰이나 알림 내용은 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatchService {

    private static final int BATCH_LIMIT = 200;

    private final PushDeliveryRepository pushDeliveryRepository;
    private final PushDeliveryRecorder pushDeliveryRecorder;
    private final PushSender pushSender;

    /** 아직 이 기기로 보내지 않은 알림을 보낸다. @return 보낸 시도 수 */
    public int dispatchNew() {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        List<PendingPush> pending = pushDeliveryRepository.findUndelivered(now.minus(PushDeliveryRules.MAX_PUSH_AGE), BATCH_LIMIT);
        int attempted = 0;
        for (PendingPush push : pending) {
            try {
                PushResult result = pushSender.send(new PushMessage(push.fcmToken(), push.title(), push.body(),
                        push.channel(), push.notificationId()));
                pushDeliveryRecorder.recordFirstAttempt(push, result, OffsetDateTime.now(ApiTime.SERVICE_ZONE));
                attempted++;
            } catch (RuntimeException e) {
                log.error("푸시 발송 처리 실패: {}", e.getClass().getSimpleName());
            }
        }
        return attempted;
    }

    /** 재시도할 차례가 된 실패를 다시 보낸다. 더 보낼 수 없는 것은 보내지 않고 마감한다. @return 다시 보낸 시도 수 */
    public int retryDue() {
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        List<RetryPush> due = pushDeliveryRepository.findDueRetries(now, BATCH_LIMIT);
        int attempted = 0;
        for (RetryPush retry : due) {
            try {
                boolean pushable = retry.pushTokenId() != null && retry.fcmToken() != null
                        && PushDeliveryRules.isPushable(retry.unread(), retry.tokenPermitted(),
                        retry.recipientCanStillSee(), retry.createdAt(), now);
                if (!pushable) {
                    pushDeliveryRecorder.giveUp(retry.deliveryId());
                    continue;
                }
                PushResult result = pushSender.send(new PushMessage(retry.fcmToken(), retry.title(), retry.body(),
                        retry.channel(), retry.notificationId()));
                pushDeliveryRecorder.recordRetryAttempt(retry, result, OffsetDateTime.now(ApiTime.SERVICE_ZONE));
                attempted++;
            } catch (RuntimeException e) {
                log.error("푸시 재시도 처리 실패: {}", e.getClass().getSimpleName());
            }
        }
        return attempted;
    }
}