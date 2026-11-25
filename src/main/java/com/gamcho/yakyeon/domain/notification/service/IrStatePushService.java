package com.gamcho.yakyeon.domain.notification.service;

import com.gamcho.yakyeon.domain.notification.push.DataPush;
import com.gamcho.yakyeon.domain.notification.push.PushResult;
import com.gamcho.yakyeon.domain.notification.push.PushSender;
import com.gamcho.yakyeon.domain.notification.repository.PouchStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 약통 IR 감지(pouch_present)가 DB에 저장된 뒤, 그 약통 복약자 본인 앱에 알림 창 없는 데이터 푸시(IR_TRIGGER)를 보낸다.
 * 앱은 이 푸시를 받으면 화면을 그리지 않고 약통 API를 다시 조회한다.
 *
 * 규칙
 *  - 저장된 뒤에만: 중계 서버가 저장하고 나서 부르고, 여기서도 저장된 상태가 있는지 DB로 확인한다 (없으면 보내지 않는다).
 *  - 받는 사람은 그 약통 복약자의 앱 계정뿐이다. 보호자 열림 알림(GUARDIAN_OPEN)과는 따로이고 notification 표에도 남기지 않는다.
 *  - 알림 설정과 상관없다: 알림 허용 여부(notification_permitted)를 보지 않는다. 문구가 없어 사용자 눈에는 보이지 않는다.
 *  - 실패해도 다시 보내지 않는다: "지금 다시 조회하라"는 신호라 늦게 가면 의미가 없고, 앱은 주기 조회로도 갱신된다. 무효 토큰만 지운다.
 *
 * 이 클래스는 일부러 @Transactional이 없다 (읽기 → 네트워크 발송 순서라 DB 행을 잡고 있지 않게 한다).
 * 로그에는 건수만 남기고 토큰, 복약자, 칸 정보는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrStatePushService {

    private final PouchStateRepository pouchStateRepository;
    private final PushSender pushSender;

    /** @return 푸시를 한 대 이상 보냈으면 true */
    public boolean push(long deviceId, long patientId, int compartmentNo) {
        if (pouchStateRepository.findPouchPresent(deviceId, compartmentNo).isEmpty()) {
            return false;   // 저장된 상태가 없다
        }
        Optional<Long> userId = pouchStateRepository.findPatientUserId(patientId);
        if (userId.isEmpty()) {
            return false;   // 앱 계정이 없는 복약자
        }
        int sent = 0;
        for (String token : pouchStateRepository.tokensOf(userId.get())) {
            try {
                PushResult result = pushSender.sendData(new DataPush(token, DataPush.IR_TRIGGER, patientId, compartmentNo));
                switch (result.status()) {
                    case SENT -> sent++;
                    case INVALID_TOKEN -> pouchStateRepository.deleteToken(token);
                    case TRANSIENT_FAILURE -> {
                        // 다시 보내지 않는다 (위 설명 참고)
                    }
                }
            } catch (RuntimeException e) {
                log.error("IR 데이터 푸시 처리 실패: {}", e.getClass().getSimpleName());
            }
        }
        if (sent > 0) {
            log.info("IR 데이터 푸시: {}건", sent);
        }
        return sent > 0;
    }
}