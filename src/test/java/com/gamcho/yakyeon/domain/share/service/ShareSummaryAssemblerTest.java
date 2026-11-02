package com.gamcho.yakyeon.domain.share.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.interaction.logic.FoundPair;
import com.gamcho.yakyeon.domain.interaction.logic.InteractionTexts;
import com.gamcho.yakyeon.domain.share.dto.ShareSummaryResponse;
import com.gamcho.yakyeon.domain.share.logic.ShareSummaryRules;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 사진(복약 요약기록 화면)의 예시를 그대로 시나리오로 삼아, 공유 요약이 어떻게 만들어지는지 확인한다. */
class ShareSummaryAssemblerTest {

    private static final ShareSummaryRules.Period PERIOD =
            new ShareSummaryRules.Period(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-27"));
    private static final Optional<LocalDate> DATASET = Optional.of(LocalDate.parse("2026-09-23"));

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    private static ShareSummaryAssembler.MedicationInput med(long id, String name, String itemSeq,
                                                             String start, String end, String endedOn) {
        return new ShareSummaryAssembler.MedicationInput(id, name, itemSeq, d(start), d(end),
                endedOn == null ? null : d(endedOn), "한의원");
    }

    private static FoundPair pair(String seqA, String seqB, String durSeq, String reason) {
        return new FoundPair(seqA, seqB, durSeq, "ING-A", "ING-B", "병용금기", reason, null, "MFDS DUR");
    }

    private static ShareSummaryResponse assemble(List<ShareSummaryAssembler.MedicationInput> meds,
                                                 Optional<LocalDate> dataset, List<FoundPair> pairs) {
        return ShareSummaryAssembler.assemble("김약연", d("1950-01-01"), PERIOD, meds, dataset, seqs -> pairs);
    }

    /** 사진: 복용 중 2개(10월 13일~30일), 복약한 약 1개(9월 20일~10월 1일), 병용금기 1건 */
    private static List<ShareSummaryAssembler.MedicationInput> photoMeds() {
        return List.of(
                med(1, "메트포르민정 500mg", "A", "2026-10-13", "2026-10-30", null),
                med(2, "아토르바스타틴정 10mg", "B", "2026-10-13", "2026-10-30", null),
                med(3, "오메프라졸캡슐 20mg", "C", "2026-09-20", "2026-10-01", null));
    }

    @Test
    @DisplayName("사진의 화면: 이름·생년월일, 30일 기간, 병용금기 1건, 복용 중 2개, 복약한 약 1개, 면책 문구")
    void photoScenario() {
        ShareSummaryResponse r = assemble(photoMeds(), DATASET, List.of(pair("A", "B", "100", "함께 복용하면 위험합니다")));

        assertThat(r.patient().name()).isEqualTo("김약연");
        assertThat(r.patient().birthDate()).isEqualTo(d("1950-01-01"));
        assertThat(r.period().from()).isEqualTo(d("2026-09-28"));
        assertThat(r.period().to()).isEqualTo(d("2026-10-27"));
        assertThat(r.period().days()).isEqualTo(30);

        assertThat(r.interactions().items().size()).isEqualTo(1);
        ShareSummaryResponse.InteractionItem item = r.interactions().items().get(0);
        assertThat(item.medicationAName()).isEqualTo("메트포르민정 500mg");
        assertThat(item.medicationBName()).isEqualTo("아토르바스타틴정 10mg");
        assertThat(item.overlapFrom()).isEqualTo(d("2026-10-13"));
        assertThat(item.overlapTo()).isEqualTo(d("2026-10-30"));
        assertThat(item.reasonText()).isEqualTo("함께 복용하면 위험합니다");
        assertThat(item.sourceName()).isEqualTo("식품의약품안전처 DUR");
        assertThat(item.sourceBaseDate()).isEqualTo(d("2026-09-23"));
        assertThat(r.interactions().uncheckedCount()).isEqualTo(0);

        assertThat(r.currentMedications().size()).isEqualTo(2);
        assertThat(r.currentMedications().get(0).drugName()).isEqualTo("메트포르민정 500mg");
        assertThat(r.currentMedications().get(0).startDate()).isEqualTo(d("2026-10-13"));
        assertThat(r.currentMedications().get(0).endDate()).isEqualTo(d("2026-10-30"));
        assertThat(r.currentMedications().get(0).hospitalName()).isEqualTo("한의원");

        assertThat(r.pastMedications().size()).isEqualTo(1);
        assertThat(r.pastMedications().get(0).drugName()).isEqualTo("오메프라졸캡슐 20mg");
        assertThat(r.pastMedications().get(0).startDate()).isEqualTo(d("2026-09-20"));
        assertThat(r.pastMedications().get(0).endDate()).isEqualTo(d("2026-10-01"));

        assertThat(r.disclaimer()).isEqualTo(InteractionTexts.SHARE_DISCLAIMER);
    }

    @Test
    @DisplayName("복약한 약과 같이 복용한 적이 없으면 병용금기에 넣지 않는다 (사진의 오메프라졸은 10월 1일에 끝남)")
    void noPairWithoutOverlap() {
        ShareSummaryResponse r = assemble(photoMeds(), DATASET,
                List.of(pair("A", "B", "100", "사유"), pair("B", "C", "200", "다른 사유"), pair("A", "C", "300", "또 다른 사유")));
        assertThat(r.interactions().items().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("공유 기간과 상관없는 약은 나오지 않는다: 기간 이전에 끝난 약, 아직 시작 전인 약, 시작하기 전에 종료한 약")
    void unrelatedMedicationsAreExcluded() {
        List<ShareSummaryAssembler.MedicationInput> meds = new ArrayList<>(photoMeds());
        meds.add(med(4, "옛 약", "D", "2026-08-01", "2026-08-10", null));
        meds.add(med(5, "아직 시작 전 약", "E", "2026-11-01", "2026-11-05", null));
        meds.add(med(6, "시작 전에 종료한 약", "F", "2026-10-20", "2026-10-30", "2026-10-20"));

        ShareSummaryResponse r = assemble(meds, DATASET, List.of());
        assertThat(r.currentMedications().size()).isEqualTo(2);
        assertThat(r.pastMedications().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("종료를 눌러 일찍 끝낸 약은 종료를 누른 전날까지로 보여 주고 복약한 약에 들어간다")
    void endedEarlyIsShownAsPast() {
        ShareSummaryResponse r = assemble(
                List.of(med(1, "일찍 끝낸 약", "A", "2026-10-13", "2026-10-30", "2026-10-20")), DATASET, List.of());
        assertThat(r.currentMedications().isEmpty()).isTrue();
        assertThat(r.pastMedications().size()).isEqualTo(1);
        assertThat(r.pastMedications().get(0).endDate()).isEqualTo(d("2026-10-19"));
    }

    @Test
    @DisplayName("복용 중은 시작일 순, 복약한 약은 최근에 끝난 순이다")
    void ordering() {
        ShareSummaryResponse r = assemble(List.of(
                med(1, "나중에 시작", "A", "2026-10-20", "2026-11-20", null),
                med(2, "먼저 시작", "B", "2026-10-01", "2026-11-20", null),
                med(3, "오래전에 끝남", "C", "2026-09-20", "2026-10-01", null),
                med(4, "최근에 끝남", "D", "2026-10-10", "2026-10-25", null)), DATASET, List.of());
        assertThat(r.currentMedications().get(0).drugName()).isEqualTo("먼저 시작");
        assertThat(r.currentMedications().get(1).drugName()).isEqualTo("나중에 시작");
        assertThat(r.pastMedications().get(0).drugName()).isEqualTo("최근에 끝남");
        assertThat(r.pastMedications().get(1).drugName()).isEqualTo("오래전에 끝남");
    }

    @Test
    @DisplayName("둘 다 공유 기간 안에 끝난 약이라도 같이 복용한 기간이 있으면 병용금기에 넣는다 (지난 이력)")
    void pastPairIsIncluded() {
        ShareSummaryResponse r = assemble(List.of(
                        med(1, "지난 약 가", "A", "2026-09-01", "2026-10-05", null),
                        med(2, "지난 약 나", "B", "2026-09-20", "2026-10-10", null)),
                DATASET, List.of(pair("A", "B", "100", "사유")));
        assertThat(r.interactions().items().size()).isEqualTo(1);
        assertThat(r.interactions().items().get(0).overlapFrom()).isEqualTo(d("2026-09-20"));
        assertThat(r.interactions().items().get(0).overlapTo()).isEqualTo(d("2026-10-05"));
        assertThat(r.currentMedications().isEmpty()).isTrue();
        assertThat(r.pastMedications().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("약물 DB와 연결되지 않은 약은 병용금기를 확인하지 못한 약으로 센다 (품목코드 없는 약은 DUR 조회에 보내지 않는다)")
    void uncheckedMedicationsAreCounted() {
        List<List<String>> asked = new ArrayList<>();
        ShareSummaryResponse r = ShareSummaryAssembler.assemble("김약연", d("1950-01-01"), PERIOD, List.of(
                        med(1, "연결된 약", "A", "2026-10-13", "2026-10-30", null),
                        med(2, "연결된 약 둘", "B", "2026-10-13", "2026-10-30", null),
                        med(3, "직접 입력한 약", null, "2026-10-13", "2026-10-30", null)),
                DATASET, seqs -> {
                    asked.add(seqs);
                    return List.of();
                });
        assertThat(r.interactions().uncheckedCount()).isEqualTo(1);
        assertThat(asked.size()).isEqualTo(1);
        assertThat(asked.get(0).size()).isEqualTo(2);
        assertThat(asked.get(0).contains("A") && asked.get(0).contains("B")).isTrue();
    }

    @Test
    @DisplayName("DUR 데이터가 아직 없으면 병용금기는 비고 모든 약이 확인하지 못한 약이다 (없다는 말이 안전하다는 뜻이 아니다)")
    void noDatasetMeansEverythingUnchecked() {
        ShareSummaryResponse r = ShareSummaryAssembler.assemble("김약연", d("1950-01-01"), PERIOD, photoMeds(),
                Optional.empty(), seqs -> {
                    throw new IllegalStateException("DUR 데이터가 없으면 조회하면 안 된다");
                });
        assertThat(r.interactions().items().isEmpty()).isTrue();
        assertThat(r.interactions().uncheckedCount()).isEqualTo(3);
        assertThat(r.currentMedications().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 약 쌍에 DUR 번호만 다르고 사유가 같으면 한 건, 사유가 다르면 각각")
    void sameReasonIsCollapsed() {
        List<ShareSummaryAssembler.MedicationInput> meds = List.of(
                med(1, "가", "A", "2026-10-13", "2026-10-30", null), med(2, "나", "B", "2026-10-13", "2026-10-30", null));
        assertThat(assemble(meds, DATASET, List.of(pair("A", "B", "100", "같은 사유"), pair("A", "B", "101", "같은 사유")))
                .interactions().items().size()).isEqualTo(1);
        assertThat(assemble(meds, DATASET, List.of(pair("A", "B", "100", "사유 하나"), pair("A", "B", "101", "사유 둘")))
                .interactions().items().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("사유가 비어 있는 규칙은 안내 문구로 바꿔서 내보낸다")
    void blankReasonIsReplaced() {
        ShareSummaryResponse r = assemble(List.of(
                        med(1, "가", "A", "2026-10-13", "2026-10-30", null), med(2, "나", "B", "2026-10-13", "2026-10-30", null)),
                DATASET, List.of(pair("A", "B", "100", "(사유 미기재)")));
        assertThat(r.interactions().items().get(0).reasonText().contains("등재되어 있지 않습니다")).isTrue();
    }

    @Test
    @DisplayName("약이 하나도 없어도 오류 없이 빈 요약을 만든다 (이름·기간·면책 문구는 그대로)")
    void emptyMedications() {
        ShareSummaryResponse r = assemble(List.of(), DATASET, List.of());
        assertThat(r.patient().name()).isEqualTo("김약연");
        assertThat(r.currentMedications().isEmpty()).isTrue();
        assertThat(r.pastMedications().isEmpty()).isTrue();
        assertThat(r.interactions().items().isEmpty()).isTrue();
        assertThat(r.interactions().uncheckedCount()).isEqualTo(0);
        assertThat(r.disclaimer().isEmpty()).isFalse();
    }

    @Test
    @DisplayName("처방 병원이 없는 약은 hospital_name이 null이다")
    void missingHospital() {
        ShareSummaryResponse r = assemble(List.of(new ShareSummaryAssembler.MedicationInput(
                1, "병원 없음", "A", d("2026-10-13"), d("2026-10-30"), null, null)), DATASET, List.of());
        assertThat(r.currentMedications().get(0).hospitalName() == null).isTrue();
    }
}