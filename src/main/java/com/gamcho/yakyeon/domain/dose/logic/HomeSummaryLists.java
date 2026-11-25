package com.gamcho.yakyeon.domain.dose.logic;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 홈 화면의 두 목록을 나눈다 (스프링·DB 없는 순수 로직).
 *
 *  - 아직 복용 체크하지 않은 약 (unchecked): 예정 시각이 지났고 아직 마감 전인 판정 전(PENDING) 예정.
 *    앱에서 "복용 체크 하기"로 기록할 수 있는 약이다. 마감이 지나면 시스템이 누락(MISSED)으로 처리해서 목록에서 빠진다.
 *    (시스템이 누락 처리한 약은 넣지 않는다: 사용자가 "안 먹었어요"를 눌러도 바뀌는 게 없어서 카드에서 치울 방법이 없고,
 *    그러면 그날은 "오늘 복용이 끝났어요"가 영원히 뜨지 않으며, 안 먹은 약을 먹었다고 누르게 만들게 된다.)
 *  - 다음 복용 예정 약 (upcoming): 예정 시각이 아직 오지 않은 판정 전(PENDING) 예정.
 *  정상·지연으로 복용을 마친 예정과 누락된 예정은 어느 목록에도 없다.
 *
 * 두 목록 모두 예정 시각이 이른 것부터 정렬한다(같으면 예정 번호 순).
 * 모든 예정은 한국 날짜 기준으로 "오늘"의 것만 들어온다(호출하는 쪽에서 오늘 것만 넘긴다).
 */
public final class HomeSummaryLists {

    private HomeSummaryLists() {
    }

    /** 나누는 데 필요한 값만 담은 예정 한 건 */
    public record Dose(long doseId, String status, OffsetDateTime scheduledAt, OffsetDateTime closeAt) {
    }

    public record Lists(List<Long> uncheckedIds, List<Long> upcomingIds) {

        /**
         * 오늘 할 복용이 모두 끝났는가: 오늘 예정이 하나라도 있고, 체크할 것도 앞으로 남은 것도 없을 때.
         * 놓쳐서 누락이 된 예정은 "끝남"을 막지 않는다(시간이 지나 더 할 수 있는 게 없다).
         * 오늘 예정이 아예 없으면(복용 중인 약이 없는 날) "끝났다"가 아니라 "예정이 없다"이므로 false다.
         */
        public boolean allDone(int totalDoses) {
            return totalDoses > 0 && uncheckedIds.isEmpty() && upcomingIds.isEmpty();
        }
    }

    public static Lists split(List<Dose> doses, OffsetDateTime now) {
        Comparator<Dose> order = Comparator.comparing(Dose::scheduledAt).thenComparingLong(Dose::doseId);
        List<Long> unchecked = doses.stream()
                .filter(d -> "PENDING".equals(d.status()) && !d.scheduledAt().isAfter(now) && d.closeAt().isAfter(now))
                .sorted(order)
                .map(Dose::doseId)
                .toList();
        List<Long> upcoming = doses.stream()
                .filter(d -> "PENDING".equals(d.status()) && d.scheduledAt().isAfter(now))
                .sorted(order)
                .map(Dose::doseId)
                .toList();
        return new Lists(unchecked, upcoming);
    }
}