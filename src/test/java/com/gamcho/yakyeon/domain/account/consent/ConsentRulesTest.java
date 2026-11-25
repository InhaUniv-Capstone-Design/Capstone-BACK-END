package com.gamcho.yakyeon.domain.account.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsentRulesTest {

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-10-30T" + time + "+09:00");
    }

    private static ConsentRules.Row active(String type, long versionId, String agreedAt) {
        return new ConsentRules.Row(type, versionId, "v" + versionId, true, at(agreedAt), null);
    }

    private static ConsentRules.Row withdrawn(String type, long versionId, String agreedAt, String withdrawnAt) {
        return new ConsentRules.Row(type, versionId, "v" + versionId, true, at(agreedAt), at(withdrawnAt));
    }

    @Test
    @DisplayName("철회한 동의는 유효하지 않고, 마지막으로 바뀐 시각은 철회 시각이다")
    void withdrawnIsNotActive() {
        ConsentRules.Row row = withdrawn("MEDICATION", 9, "09:00:00", "10:00:00");
        assertThat(row.isActive()).isFalse();
        assertThat(row.changedAt()).isEqualTo(at("10:00:00"));
        assertThat(active("MEDICATION", 9, "09:00:00").isActive()).isTrue();
        assertThat(active("MEDICATION", 9, "09:00:00").changedAt()).isEqualTo(at("09:00:00"));
    }

    @Test
    @DisplayName("agreed가 false인 행은 철회하지 않았어도 유효하지 않다")
    void notAgreedIsNotActive() {
        ConsentRules.Row row = new ConsentRules.Row("TOS", 1, "v1", false, at("09:00:00"), null);
        assertThat(row.isActive()).isFalse();
    }

    @Test
    @DisplayName("약관 종류마다 가장 최근에 동의한 행 하나를 현재 상태로 보여 준다")
    void picksLatestPerType() {
        List<ConsentRules.State> states = ConsentRules.currentStates(List.of(
                active("TOS", 1, "08:00:00"),
                active("PRIVACY", 2, "08:10:00"),
                withdrawn("MEDICATION", 9, "09:00:00", "09:30:00")));
        assertThat(states.size()).isEqualTo(3);
        assertThat(states.get(0).termsType()).isEqualTo("TOS");
        assertThat(states.get(2).termsType()).isEqualTo("MEDICATION");
        assertThat(states.get(2).agreed()).isFalse();
    }

    @Test
    @DisplayName("같은 종류에 버전이 둘이면 더 늦게 동의한 버전이 현재 상태다")
    void newerVersionWins() {
        List<ConsentRules.State> states = ConsentRules.currentStates(List.of(
                withdrawn("DEVICE", 3, "08:00:00", "08:30:00"),
                active("DEVICE", 5, "09:00:00")));
        assertThat(states.size()).isEqualTo(1);
        assertThat(states.get(0).termsVersionId()).isEqualTo(5L);
        assertThat(states.get(0).agreed()).isTrue();
    }

    @Test
    @DisplayName("옛 버전을 철회하고 새 버전에는 아직 동의하지 않았다면 철회된 옛 버전이 보인다")
    void withdrawnOldVersionShownWhenNoNewAgreement() {
        List<ConsentRules.State> states = ConsentRules.currentStates(List.of(
                withdrawn("PRIVACY", 2, "08:00:00", "08:30:00")));
        assertThat(states.get(0).agreed()).isFalse();
        assertThat(states.get(0).termsVersionId()).isEqualTo(2L);
        assertThat(states.get(0).changedAt()).isEqualTo(at("08:30:00"));
    }

    @Test
    @DisplayName("동의 시각이 같으면 버전 번호가 큰 쪽이 현재 상태다")
    void tieBreaksByVersionId() {
        List<ConsentRules.State> states = ConsentRules.currentStates(List.of(
                active("TOS", 4, "09:00:00"), active("TOS", 7, "09:00:00")));
        assertThat(states.get(0).termsVersionId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("한 번도 동의하지 않은 종류와 이 화면에서 다루지 않는 종류는 목록에 없다")
    void omitsUnknownAndUnlistedTypes() {
        List<ConsentRules.State> states = ConsentRules.currentStates(List.of(
                active("DELEGATION", 11, "09:00:00"), active("LEGAL_REP", 12, "09:00:00")));
        assertThat(states.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("목록은 항상 이용약관, 개인정보, 건강정보, 이용 고지, 약물, 약통 순서다")
    void fixedOrder() {
        List<ConsentRules.State> states = ConsentRules.currentStates(List.of(
                active("DEVICE", 6, "09:00:00"), active("MEDICATION", 5, "09:00:00"),
                active("SERVICE_NOTICE", 4, "09:00:00"), active("SENSITIVE_HEALTH", 3, "09:00:00"),
                active("PRIVACY", 2, "09:00:00"), active("TOS", 1, "09:00:00")));
        assertThat(states.size()).isEqualTo(6);
        assertThat(states.get(0).termsType()).isEqualTo("TOS");
        assertThat(states.get(1).termsType()).isEqualTo("PRIVACY");
        assertThat(states.get(2).termsType()).isEqualTo("SENSITIVE_HEALTH");
        assertThat(states.get(3).termsType()).isEqualTo("SERVICE_NOTICE");
        assertThat(states.get(4).termsType()).isEqualTo("MEDICATION");
        assertThat(states.get(5).termsType()).isEqualTo("DEVICE");
    }

    @Test
    @DisplayName("동의·철회할 수 있는 종류: 서비스 이용 고지는 동의만 되고 철회할 수 없다")
    void agreeableAndWithdrawable() {
        assertThat(ConsentRules.isAgreeable("SERVICE_NOTICE")).isTrue();
        assertThat(ConsentRules.isWithdrawable("SERVICE_NOTICE")).isFalse();
        assertThat(ConsentRules.isAgreeable("MEDICATION")).isTrue();
        assertThat(ConsentRules.isWithdrawable("MEDICATION")).isTrue();
        assertThat(ConsentRules.isWithdrawable("DEVICE")).isTrue();
        assertThat(ConsentRules.isAgreeable("DELEGATION")).isFalse();
        assertThat(ConsentRules.isWithdrawable("LEGAL_REP")).isFalse();
        assertThat(ConsentRules.isAgreeable("NO_SUCH")).isFalse();
        assertThat(ConsentRules.isAgreeable(null)).isFalse();
        assertThat(ConsentRules.isWithdrawable(null)).isFalse();
    }

    @Test
    @DisplayName("서비스 필수 동의는 이용약관, 개인정보, 건강정보 세 가지다")
    void serviceRequired() {
        assertThat(ConsentRules.SERVICE_REQUIRED.size()).isEqualTo(3);
        assertThat(ConsentRules.SERVICE_REQUIRED.contains("TOS")).isTrue();
        assertThat(ConsentRules.SERVICE_REQUIRED.contains("PRIVACY")).isTrue();
        assertThat(ConsentRules.SERVICE_REQUIRED.contains("SENSITIVE_HEALTH")).isTrue();
        assertThat(ConsentRules.SERVICE_REQUIRED.contains("SERVICE_NOTICE")).isFalse();
    }
}