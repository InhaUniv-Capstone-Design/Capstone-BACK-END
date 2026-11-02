package com.gamcho.yakyeon.domain.device.logic;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 약통 화면의 "확인 필요" 표시 규칙.
 *
 * 칸을 열었지만 복용 확인 버튼이 눌리지 않은 상태("열림만 있음")가 최근 {@link #WINDOW} 안에 있고, 그 뒤에 그 칸의
 * 복용 확인이 없으면 앱이 "약통이 열렸습니다"를 보여 준다. 실제 조회는 PillboxQueryService의 SQL이 하고,
 * 이 클래스는 그 기준 시각과 표시 값을 정한다.
 */
public final class AttentionRules {

    /** 칸에 표시하는 값. 확인할 것이 없으면 값이 없다(null) */
    public static final String OPENED = "OPENED";

    /** 열림만 있는 이벤트를 이 시간 동안만 "확인 필요"로 본다 */
    public static final Duration WINDOW = Duration.ofMinutes(60);

    private AttentionRules() {
    }

    /** 이 시각 이후(포함)의 열림만 있음 이벤트가 대상이다 */
    public static OffsetDateTime since(OffsetDateTime now) {
        return now.minus(WINDOW);
    }
}