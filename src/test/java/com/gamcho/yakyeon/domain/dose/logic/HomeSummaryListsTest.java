package com.gamcho.yakyeon.domain.dose.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.dose.logic.HomeSummaryLists.Dose;
import com.gamcho.yakyeon.domain.dose.logic.HomeSummaryLists.Lists;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HomeSummaryListsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-09T12:20:00+09:00");

    private static OffsetDateTime at(String hhmm) {
        return OffsetDateTime.parse("2026-11-09T" + hhmm + ":00+09:00");
    }

    /** 마감은 예정 3시간 뒤 (일반적인 설정) */
    private static Dose dose(long id, String status, String hhmm) {
        OffsetDateTime scheduled = at(hhmm);
        return new Dose(id, status, scheduled, scheduled.plusHours(3));
    }

    @Test
    @DisplayName("새 홈 화면 시안과 같은 상황: 지난 점심은 체크 안 한 약, 이후는 다음 예정")
    void designScenario() {
        List<Dose> doses = List.of(
                dose(1, "NORMAL", "08:10"),    // 아침(한의원) 복용 완료
                dose(2, "NORMAL", "08:30"),    // 아침(내과) 복용 완료
                dose(3, "PENDING", "12:10"),   // 점심 한의원 - 지났고 체크 안 함
                dose(4, "PENDING", "12:30"),   // 점심 내과 - 12:20 현재는 아직 오지 않음
                dose(5, "PENDING", "17:10"),
                dose(6, "PENDING", "17:20"),
                dose(7, "PENDING", "22:00"));
        Lists lists = HomeSummaryLists.split(doses, NOW);
        assertThat(lists.uncheckedIds().toString()).isEqualTo("[3]");
        assertThat(lists.upcomingIds().toString()).isEqualTo("[4, 5, 6, 7]");
        assertThat(lists.allDone(doses.size())).isFalse();
    }

    @Test
    @DisplayName("예정 시각이 정확히 지금이면 체크할 때가 된 것이다 (다음 예정이 아니다)")
    void exactlyNowIsUnchecked() {
        Lists lists = HomeSummaryLists.split(List.of(new Dose(1, "PENDING", NOW, NOW.plusHours(3))), NOW);
        assertThat(lists.uncheckedIds().toString()).isEqualTo("[1]");
        assertThat(lists.upcomingIds().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("마감이 지나 시스템이 누락 처리한 약은 체크 안 한 약이 아니다 (카드에서 치울 방법이 없어서 완료가 영원히 안 뜬다)")
    void systemMissedIsNotListed() {
        Lists lists = HomeSummaryLists.split(List.of(dose(1, "MISSED", "08:10")), NOW);
        assertThat(lists.uncheckedIds().isEmpty()).isTrue();
        assertThat(lists.upcomingIds().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("마감이 지났는데 아직 누락으로 바뀌기 전인 약도 목록에 없다 (곧 누락 처리된다)")
    void pendingPastCloseIsNotListed() {
        Dose lapsed = new Dose(1, "PENDING", at("08:00"), at("12:20"));   // 마감이 정확히 지금
        Dose lapsedEarlier = new Dose(2, "PENDING", at("07:00"), at("10:00"));
        Lists lists = HomeSummaryLists.split(List.of(lapsed, lapsedEarlier), NOW);
        assertThat(lists.uncheckedIds().isEmpty() && lists.upcomingIds().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("정상·지연으로 복용한 예정은 어느 목록에도 없다")
    void takenDosesAreInNeitherList() {
        Lists lists = HomeSummaryLists.split(List.of(dose(1, "NORMAL", "08:10"), dose(2, "DELAYED", "12:10")), NOW);
        assertThat(lists.uncheckedIds().isEmpty() && lists.upcomingIds().isEmpty()).isTrue();
        assertThat(lists.allDone(2)).isTrue();
    }

    @Test
    @DisplayName("오늘 복용이 모두 끝났다: 목록 둘이 비어 있고 오늘 예정이 있을 때만")
    void allDoneRequiresTodaysDoses() {
        assertThat(HomeSummaryLists.split(List.of(), NOW).allDone(0)).as("예정이 하나도 없는 날은 '끝났다'가 아니다").isFalse();
        Lists finished = HomeSummaryLists.split(List.of(dose(1, "NORMAL", "08:10"), dose(2, "NORMAL", "12:00")), NOW);
        assertThat(finished.allDone(2)).isTrue();
    }

    @Test
    @DisplayName("놓친(누락) 예정이 있어도 더 할 게 없으면 오늘 복용은 끝난 것이다")
    void missedDosesDoNotBlockCompletion() {
        Lists lists = HomeSummaryLists.split(List.of(dose(1, "NORMAL", "08:10"), dose(2, "MISSED", "09:00"), dose(3, "MISSED", "10:00")), NOW);
        assertThat(lists.allDone(3)).isTrue();
    }

    @Test
    @DisplayName("체크할 약이 있거나 남은 예정이 있으면 끝난 게 아니다")
    void notDoneWhileAnythingRemains() {
        assertThat(HomeSummaryLists.split(List.of(dose(1, "PENDING", "12:00")), NOW).allDone(1)).isFalse();
        assertThat(HomeSummaryLists.split(List.of(dose(1, "NORMAL", "08:00"), dose(2, "PENDING", "22:00")), NOW).allDone(2)).isFalse();
    }

    @Test
    @DisplayName("두 목록은 예정 시각이 이른 순이고, 같으면 예정 번호 순이다")
    void sortedByTimeThenId() {
        Lists lists = HomeSummaryLists.split(List.of(
                dose(9, "PENDING", "12:10"), dose(3, "PENDING", "12:10"), dose(5, "PENDING", "11:00"),
                dose(8, "PENDING", "23:00"), dose(7, "PENDING", "17:00"), dose(6, "PENDING", "17:00")), NOW);
        assertThat(lists.uncheckedIds().toString()).isEqualTo("[5, 3, 9]");
        assertThat(lists.upcomingIds().toString()).isEqualTo("[6, 7, 8]");
    }

    @Test
    @DisplayName("시각 비교는 시간대(+09:00, UTC)와 상관없이 같은 순간으로 본다")
    void comparesInstantsAcrossZones() {
        OffsetDateTime utc = OffsetDateTime.parse("2026-11-09T03:10:00Z");   // 한국 12:10
        Lists lists = HomeSummaryLists.split(List.of(new Dose(1, "PENDING", utc, utc.plusHours(3))), NOW);
        assertThat(lists.uncheckedIds().toString()).isEqualTo("[1]");
    }

    @Test
    @DisplayName("취침 예정(22:00)은 밤 10시 전에는 다음 예정, 그 뒤에는 체크 안 한 약이다")
    void bedtimeMovesBetweenLists() {
        List<Dose> doses = List.of(dose(1, "NORMAL", "08:00"), new Dose(2, "PENDING", at("22:00"), at("23:59")));
        assertThat(HomeSummaryLists.split(doses, OffsetDateTime.parse("2026-11-09T21:59:00+09:00")).upcomingIds().toString()).isEqualTo("[2]");
        assertThat(HomeSummaryLists.split(doses, OffsetDateTime.parse("2026-11-09T22:00:00+09:00")).uncheckedIds().toString()).isEqualTo("[2]");
    }
}