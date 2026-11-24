package com.gamcho.yakyeon.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.notification.push.DataPush;
import com.gamcho.yakyeon.domain.notification.push.PushMessage;
import com.gamcho.yakyeon.domain.notification.push.PushResult;
import com.gamcho.yakyeon.domain.notification.push.PushSender;
import com.gamcho.yakyeon.domain.notification.repository.PouchStateRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DB와 FCM 없이, 가짜 저장소와 가짜 발송자로 "저장된 상태가 있으면 복약자 앱에만 데이터 푸시" 흐름을 시험한다 */
class IrStatePushServiceTest {

    /** 상태, 계정, 토큰을 메모리에 두는 가짜 저장소 */
    private static final class FakeRepo extends PouchStateRepository {
        boolean hasState = true;
        Long userId = 77L;
        final Map<Long, List<String>> tokens = new HashMap<>();
        final List<String> deleted = new ArrayList<>();
        final List<Long> tokenLookups = new ArrayList<>();

        FakeRepo() {
            super(null);
        }

        @Override
        public Optional<Boolean> findPouchPresent(long deviceId, int compartmentNo) {
            return hasState ? Optional.of(true) : Optional.empty();
        }

        @Override
        public Optional<Long> findPatientUserId(long patientId) {
            return Optional.ofNullable(userId);
        }

        @Override
        public List<String> tokensOf(long userId) {
            tokenLookups.add(userId);
            return tokens.getOrDefault(userId, List.of());
        }

        @Override
        public void deleteToken(String fcmToken) {
            deleted.add(fcmToken);
        }
    }

    /** 보낸 데이터 푸시를 모으고, 토큰 이름으로 결과를 정하는 가짜 발송자 (bad-: 무효, fail-: 일시 실패, boom-: 예외) */
    private static final class FakeSender implements PushSender {
        final List<DataPush> sent = new ArrayList<>();

        @Override
        public PushResult send(PushMessage message) {
            throw new AssertionError("일반 푸시는 보내지 않는다");
        }

        @Override
        public PushResult sendData(DataPush push) {
            sent.add(push);
            if (push.fcmToken().startsWith("boom-")) {
                throw new IllegalStateException("예상 못한 오류");
            }
            if (push.fcmToken().startsWith("bad-")) {
                return PushResult.invalidToken();
            }
            if (push.fcmToken().startsWith("fail-")) {
                return PushResult.transientFailure("NETWORK");
            }
            return PushResult.sent("m");
        }
    }

    private final FakeRepo repo = new FakeRepo();
    private final FakeSender sender = new FakeSender();
    private final IrStatePushService service = new IrStatePushService(repo, sender);

    @Test
    @DisplayName("저장된 상태가 있으면 복약자 계정의 모든 기기에 IR_TRIGGER를 보낸다")
    void sendsToAllDevicesOfThePatientAccount() {
        repo.tokens.put(77L, List.of("tok-a", "tok-b"));
        assertThat(service.push(5L, 10L, 2)).isTrue();
        assertThat(sender.sent).hasSize(2);
        DataPush first = sender.sent.get(0);
        assertThat(first.type()).isEqualTo("IR_TRIGGER");
        assertThat(first.patientId()).isEqualTo(10L);
        assertThat(first.compartmentNo()).isEqualTo(2);
        assertThat(repo.tokenLookups).as("받는 사람은 복약자 계정(77)뿐").containsOnly(77L);
    }

    @Test
    @DisplayName("저장된 상태가 아직 없으면 보내지 않는다 (저장 전에 부른 경우)")
    void doesNotSendWithoutStoredState() {
        repo.tokens.put(77L, List.of("tok-a"));
        repo.hasState = false;
        assertThat(service.push(5L, 10L, 1)).isFalse();
        assertThat(sender.sent).isEmpty();
        assertThat(repo.tokenLookups).isEmpty();
    }

    @Test
    @DisplayName("앱 계정이 없는 복약자는 토큰을 찾지도 보내지도 않는다")
    void noAccountNoPush() {
        repo.userId = null;
        assertThat(service.push(5L, 10L, 1)).isFalse();
        assertThat(repo.tokenLookups).isEmpty();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    @DisplayName("로그인한 기기가 없으면(토큰 없음) 보내지 않고 false")
    void noTokensNoPush() {
        assertThat(service.push(5L, 10L, 1)).isFalse();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    @DisplayName("무효 토큰은 지우고, 일시 실패와 예외는 다시 보내지 않으며, 다른 기기 발송은 계속된다")
    void handlesFailuresPerToken() {
        repo.tokens.put(77L, List.of("bad-1", "fail-1", "boom-1", "ok-1"));
        assertThat(service.push(5L, 10L, 1)).as("성공한 기기가 하나 있다").isTrue();
        assertThat(repo.deleted).containsExactly("bad-1");
        assertThat(sender.sent).hasSize(4);
    }

    @Test
    @DisplayName("모두 실패하면 false다")
    void allFailedReturnsFalse() {
        repo.tokens.put(77L, List.of("bad-1", "fail-1"));
        assertThat(service.push(5L, 10L, 1)).isFalse();
    }
}