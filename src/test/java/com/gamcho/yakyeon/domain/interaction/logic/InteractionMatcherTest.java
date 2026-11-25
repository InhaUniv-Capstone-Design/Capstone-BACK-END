package com.gamcho.yakyeon.domain.interaction.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InteractionMatcherTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-27");

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    private static MedicationRef med(long id, String itemSeq, String start, String end) {
        return new MedicationRef(id, "약" + id, itemSeq, d(start), d(end));
    }

    private static FoundPair pair(String seqA, String seqB, String durSeq) {
        return new FoundPair(seqA, seqB, durSeq, "ING-" + seqA, "ING-" + seqB, "병용금기", "사유 " + durSeq, null, "MFDS DUR");
    }

    // ---- 오늘 기준 (지금 복용 중인 약의 병용금기)

    @Test
    @DisplayName("둘 다 복용 중이고 기간이 겹치면 병용금기 한 건")
    void bothActiveOverlapping() {
        List<Finding> found = InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-20", "2026-11-03"), med(2, "B", "2026-10-25", "2026-11-05")),
                List.of(pair("A", "B", "100")), TODAY);
        assertThat(found.size()).isEqualTo(1);
        assertThat(found.get(0).medicationAId()).isEqualTo(1L);
        assertThat(found.get(0).medicationBId()).isEqualTo(2L);
        assertThat(found.get(0).durSeq()).isEqualTo("100");
        assertThat(found.get(0).sourceName()).isEqualTo("MFDS DUR");
    }

    @Test
    @DisplayName("이미 끝난 약이 낀 쌍은 뺀다 (종료일이 오늘이면 아직 복용 중이라 포함)")
    void endedMedicationIsExcluded() {
        List<FoundPair> pairs = List.of(pair("A", "B", "100"));
        assertThat(InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-01", "2026-10-26"), med(2, "B", "2026-10-01", "2026-11-05")), pairs, TODAY).size()).isEqualTo(0);
        assertThat(InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-01", "2026-10-27"), med(2, "B", "2026-10-01", "2026-11-05")), pairs, TODAY).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("복용 기간이 하루도 겹치지 않으면 뺀다 (하루만 겹쳐도 포함)")
    void nonOverlappingIsExcluded() {
        List<FoundPair> pairs = List.of(pair("A", "B", "100"));
        assertThat(InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-27", "2026-10-30"), med(2, "B", "2026-10-31", "2026-11-05")), pairs, TODAY).size()).isEqualTo(0);
        assertThat(InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-27", "2026-10-31"), med(2, "B", "2026-10-31", "2026-11-05")), pairs, TODAY).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("약 번호가 큰 약의 품목이 쌍의 앞쪽이어도 번호가 작은 약이 A가 되고 성분 코드도 함께 바뀐다")
    void smallerIdIsA() {
        Finding f = InteractionMatcher.match(
                List.of(med(5, "A", "2026-10-20", "2026-11-03"), med(2, "B", "2026-10-20", "2026-11-03")),
                List.of(pair("A", "B", "100")), TODAY).get(0);
        assertThat(f.medicationAId()).isEqualTo(2L);
        assertThat(f.medicationBId()).isEqualTo(5L);
        assertThat(f.ingredientACode()).isEqualTo("ING-B");
        assertThat(f.ingredientBCode()).isEqualTo("ING-A");
    }

    @Test
    @DisplayName("약물 DB와 연결되지 않은 약(품목코드 없음)은 검사하지 않는다")
    void uncheckableMedicationIsIgnored() {
        List<Finding> found = InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-20", "2026-11-03"), med(2, null, "2026-10-20", "2026-11-03")),
                List.of(pair("A", "B", "100")), TODAY);
        assertThat(found.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("같은 쌍이 같은 DUR 번호로 두 번 들어와도 한 건만 남는다")
    void duplicatesAreRemoved() {
        List<Finding> found = InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-20", "2026-11-03"), med(2, "B", "2026-10-20", "2026-11-03")),
                List.of(pair("A", "B", "100"), pair("A", "B", "100")), TODAY);
        assertThat(found.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 약을 두 처방에서 받아 둘 다 등록했다면 각각 상대 약과 짝이 된다")
    void sameDrugTwice() {
        List<Finding> found = InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-20", "2026-11-03"), med(2, "A", "2026-10-20", "2026-11-03"),
                        med(3, "B", "2026-10-20", "2026-11-03")),
                List.of(pair("A", "B", "100")), TODAY);
        assertThat(found.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("결과는 약 번호 쌍과 DUR 번호 순으로 정렬된다")
    void sortedResult() {
        List<Finding> found = InteractionMatcher.match(
                List.of(med(1, "A", "2026-10-20", "2026-11-03"), med(2, "B", "2026-10-20", "2026-11-03"),
                        med(3, "C", "2026-10-20", "2026-11-03")),
                List.of(pair("B", "C", "300"), pair("A", "B", "200"), pair("A", "B", "100")), TODAY);
        assertThat(found.size()).isEqualTo(3);
        assertThat(found.get(0).durSeq()).isEqualTo("100");
        assertThat(found.get(1).durSeq()).isEqualTo("200");
        assertThat(found.get(2).medicationAId()).isEqualTo(2L);
    }

    // ---- 기준을 바꿔 끼우는 경우 (공유 요약: 지난 복용 이력)

    @Test
    @DisplayName("관련 쌍 기준을 직접 정하면 이미 끝난 약이 낀 쌍도 찾을 수 있다 (지난 복용 이력)")
    void customRelevance() {
        List<MedicationRef> meds = List.of(med(1, "A", "2026-10-01", "2026-10-10"), med(2, "B", "2026-10-05", "2026-10-30"));
        List<FoundPair> pairs = List.of(pair("A", "B", "100"));

        assertThat(InteractionMatcher.match(meds, pairs, TODAY).size()).as("오늘 기준은 끝난 약이 있어 제외").isEqualTo(0);
        assertThat(InteractionMatcher.match(meds, pairs, (a, b) -> true).size()).as("기준을 바꾸면 포함").isEqualTo(1);
    }

    @Test
    @DisplayName("관련 쌍 기준이 거절하면 그 쌍은 빠진다")
    void customRelevanceRejects() {
        List<MedicationRef> meds = List.of(med(1, "A", "2026-10-01", "2026-10-10"), med(2, "B", "2026-10-05", "2026-10-30"));
        assertThat(InteractionMatcher.match(meds, List.of(pair("A", "B", "100")), (a, b) -> false).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("오늘 기준 호출과 같은 기준을 직접 넘긴 호출의 결과는 같다")
    void todayOverloadDelegates() {
        List<MedicationRef> meds = List.of(med(1, "A", "2026-10-20", "2026-11-03"), med(2, "B", "2026-10-20", "2026-11-03"));
        List<FoundPair> pairs = List.of(pair("A", "B", "100"));
        assertThat(InteractionMatcher.match(meds, pairs, TODAY))
                .isEqualTo(InteractionMatcher.match(meds, pairs, (a, b) -> InteractionMatcher.isRelevantPair(a, b, TODAY)));
    }
}