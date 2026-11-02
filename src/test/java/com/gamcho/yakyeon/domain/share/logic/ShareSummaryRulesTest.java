package com.gamcho.yakyeon.domain.share.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.gamcho.yakyeon.domain.interaction.logic.MedicationRef;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShareSummaryRulesTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-27");

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    private static ShareSummaryRules.Period period(String from, String to) {
        return new ShareSummaryRules.Period(d(from), d(to));
    }

    // ---- 기간

    @Test
    @DisplayName("기간을 안 주면 오늘까지 최근 30일(오늘 포함): 10월 27일이면 9월 28일부터")
    void defaultPeriod() {
        ShareSummaryRules.Period p = ShareSummaryRules.resolvePeriod(null, null, TODAY);
        assertThat(p.from()).isEqualTo(d("2026-09-28"));
        assertThat(p.to()).isEqualTo(d("2026-10-27"));
        assertThat(p.days()).isEqualTo(30);
    }

    @Test
    @DisplayName("직접 고른 기간은 양 끝을 포함해서 날짜 수를 센다")
    void customPeriodDays() {
        assertThat(ShareSummaryRules.resolvePeriod(d("2026-10-21"), d("2026-10-27"), TODAY).days()).isEqualTo(7);
        assertThat(ShareSummaryRules.resolvePeriod(d("2026-10-27"), d("2026-10-27"), TODAY).days()).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 90일까지 허용하고 91일은 거절한다")
    void maxDays() {
        // 2026-10-27에서 89일 전 = 2026-07-30 (90일), 90일 전 = 2026-07-29 (91일)
        assertThat(ShareSummaryRules.resolvePeriod(d("2026-07-30"), TODAY, TODAY).days()).isEqualTo(90);
        try {
            ShareSummaryRules.resolvePeriod(d("2026-07-29"), TODAY, TODAY);
            assertThat(false).as("91일은 거절해야 한다").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains("90")).isTrue();
        }
    }

    @Test
    @DisplayName("from과 to 중 하나만 주면 거절한다")
    void onlyOneEnd() {
        for (LocalDate[] pair : new LocalDate[][]{{d("2026-10-01"), null}, {null, d("2026-10-01")}}) {
            try {
                ShareSummaryRules.resolvePeriod(pair[0], pair[1], TODAY);
                assertThat(false).isTrue();
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage().contains("함께")).isTrue();
            }
        }
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦거나, 종료일이 오늘 이후면 거절한다")
    void invalidOrder() {
        for (LocalDate[] pair : new LocalDate[][]{{d("2026-10-20"), d("2026-10-10")}, {d("2026-10-20"), d("2026-10-28")}}) {
            try {
                ShareSummaryRules.resolvePeriod(pair[0], pair[1], TODAY);
                assertThat(false).isTrue();
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage().isEmpty()).isFalse();
            }
        }
    }

    // ---- 실제 복용한 마지막 날

    @Test
    @DisplayName("종료를 누른 적이 없으면 계획한 종료일이 마지막 복용일이다")
    void lastActiveWithoutEnding() {
        assertThat(ShareSummaryRules.lastActiveDate(d("2026-10-30"), null)).isEqualTo(d("2026-10-30"));
    }

    @Test
    @DisplayName("종료를 누른 날부터는 종료된 약이라 그 전날까지가 복용한 날이다")
    void lastActiveWhenEndedEarly() {
        assertThat(ShareSummaryRules.lastActiveDate(d("2026-10-30"), d("2026-10-20"))).isEqualTo(d("2026-10-19"));
    }

    @Test
    @DisplayName("계획한 종료일이 종료를 누른 전날보다 빠르면 계획한 종료일이다")
    void lastActiveWhenPlannedEarlier() {
        assertThat(ShareSummaryRules.lastActiveDate(d("2026-10-10"), d("2026-10-20"))).isEqualTo(d("2026-10-10"));
    }

    // ---- 복용 중 / 복약한 / 해당 없음

    private static ShareSummaryRules.Kind kind(String start, String last) {
        return ShareSummaryRules.classify(d(start), d(last), period("2026-09-28", "2026-10-27"));
    }

    @Test
    @DisplayName("사진의 예시: 10월 13일~30일 약은 복용 중, 9월 20일~10월 1일 약은 복약한 약")
    void photoExample() {
        assertThat(kind("2026-10-13", "2026-10-30")).isEqualTo(ShareSummaryRules.Kind.CURRENT);
        assertThat(kind("2026-09-20", "2026-10-01")).isEqualTo(ShareSummaryRules.Kind.PAST);
    }

    @Test
    @DisplayName("공유 기간 마지막 날에 끝나는 약은 복용 중, 하루 전에 끝났으면 복약한 약")
    void endBoundary() {
        assertThat(kind("2026-10-01", "2026-10-27")).isEqualTo(ShareSummaryRules.Kind.CURRENT);
        assertThat(kind("2026-10-01", "2026-10-26")).isEqualTo(ShareSummaryRules.Kind.PAST);
    }

    @Test
    @DisplayName("공유 기간 첫날에 끝난 약은 하루라도 겹치니 복약한 약, 그 전날 끝났으면 해당 없음")
    void startBoundary() {
        assertThat(kind("2026-09-01", "2026-09-28")).isEqualTo(ShareSummaryRules.Kind.PAST);
        assertThat(kind("2026-09-01", "2026-09-27")).isEqualTo(ShareSummaryRules.Kind.NONE);
    }

    @Test
    @DisplayName("마지막 날에 시작하는 약은 복용 중, 그다음 날 시작하는 약(아직 시작 전)은 해당 없음")
    void notYetStarted() {
        assertThat(kind("2026-10-27", "2026-11-05")).isEqualTo(ShareSummaryRules.Kind.CURRENT);
        assertThat(kind("2026-10-28", "2026-11-05")).isEqualTo(ShareSummaryRules.Kind.NONE);
    }

    @Test
    @DisplayName("시작하기도 전에 종료한 약(마지막 복용일이 시작일보다 앞)은 해당 없음")
    void endedBeforeItStarted() {
        assertThat(kind("2026-10-20", "2026-10-19")).isEqualTo(ShareSummaryRules.Kind.NONE);
    }

    // ---- 함께 복용한 기간

    private static MedicationRef med(long id, String start, String last) {
        return new MedicationRef(id, "약" + id, "SEQ" + id, d(start), d(last));
    }

    @Test
    @DisplayName("사진의 예시: 두 약이 10월 13일~30일 같이 복용 -> 함께 복용한 기간 10월 13일~30일 (공유 기간 밖까지 이어져도 그대로)")
    void togetherPhotoExample() {
        Optional<ShareSummaryRules.DateRange> range = ShareSummaryRules.togetherInPeriod(
                med(1, "2026-10-13", "2026-10-30"), med(2, "2026-10-13", "2026-10-30"), period("2026-09-28", "2026-10-27"));
        assertThat(range.isPresent()).isTrue();
        assertThat(range.get().from()).isEqualTo(d("2026-10-13"));
        assertThat(range.get().to()).isEqualTo(d("2026-10-30"));
    }

    @Test
    @DisplayName("함께 복용한 기간은 두 약의 복용일의 교집합이다")
    void togetherIsIntersection() {
        ShareSummaryRules.DateRange range = ShareSummaryRules.togetherInPeriod(
                med(1, "2026-10-01", "2026-10-20"), med(2, "2026-10-10", "2026-10-30"), period("2026-09-28", "2026-10-27")).get();
        assertThat(range.from()).isEqualTo(d("2026-10-10"));
        assertThat(range.to()).isEqualTo(d("2026-10-20"));
    }

    @Test
    @DisplayName("하루만 겹쳐도 함께 복용한 것이고, 하루도 안 겹치면 아니다")
    void touchingDays() {
        ShareSummaryRules.Period p = period("2026-09-28", "2026-10-27");
        assertThat(ShareSummaryRules.togetherInPeriod(med(1, "2026-10-01", "2026-10-10"), med(2, "2026-10-10", "2026-10-20"), p).isPresent()).isTrue();
        assertThat(ShareSummaryRules.togetherInPeriod(med(1, "2026-10-01", "2026-10-09"), med(2, "2026-10-10", "2026-10-20"), p).isPresent()).isFalse();
    }

    @Test
    @DisplayName("함께 복용했지만 공유 기간이 시작되기 전에 끝났거나 끝난 뒤에 시작했다면 포함하지 않는다")
    void togetherOutsidePeriod() {
        ShareSummaryRules.Period p = period("2026-09-28", "2026-10-27");
        assertThat(ShareSummaryRules.togetherInPeriod(med(1, "2026-08-01", "2026-09-27"), med(2, "2026-08-10", "2026-09-20"), p).isPresent()).isFalse();
        assertThat(ShareSummaryRules.togetherInPeriod(med(1, "2026-10-28", "2026-11-10"), med(2, "2026-10-28", "2026-11-05"), p).isPresent()).isFalse();
    }

    @Test
    @DisplayName("공유 기간 첫날이나 마지막 날 하루만 같이 복용해도 포함한다")
    void togetherOnPeriodEdge() {
        ShareSummaryRules.Period p = period("2026-09-28", "2026-10-27");
        assertThat(ShareSummaryRules.togetherInPeriod(med(1, "2026-09-01", "2026-09-28"), med(2, "2026-09-28", "2026-10-05"), p).isPresent()).isTrue();
        assertThat(ShareSummaryRules.togetherInPeriod(med(1, "2026-10-27", "2026-11-05"), med(2, "2026-10-20", "2026-10-27"), p).isPresent()).isTrue();
    }
}