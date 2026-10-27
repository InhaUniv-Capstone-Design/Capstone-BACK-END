package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 신호 처리 결과. 기기는 받았다는 확인만 필요하지만, 개발 중에 무슨 일이 일어났는지 볼 수 있게 결과를 돌려준다.
 * 값이 없는 항목은 응답에서 빠진다.
 *
 * outcome:
 *  DUPLICATE                 - 이미 받은 신호(재전송). 아무것도 바꾸지 않았다
 *  WAITING_FOR_BUTTON        - 칸 열림을 받았고, 허용 시간 안에 버튼을 기다리는 중
 *  CONFIRMED                 - 열림과 버튼이 짝이 되어 복용으로 확인됨
 *  BUTTON_ONLY               - 열림 없이 버튼만 눌림 (센서 오류 가능성) - 복용으로 확인됨
 *  IGNORED_REPEATED_PRESS    - 복용 직후 허용 시간 안의 반복 버튼 (무시)
 *  IGNORED_AFTER_TAKEN       - 복용 직후 허용 시간 안의 열림 (무시)
 *  IGNORED_NO_ACTIVE_MAPPING - 이 칸에 충전이 확인된 약봉지가 없음 (무시)
 *
 * judgement: 복용이 확인됐을 때만. NORMAL / DELAYED / UNSCHEDULED(먹을 차례인 예정이 없음)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceSignalResponse(
        @JsonProperty("signal_id") Long signalId,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("judgement") String judgement,
        @JsonProperty("scheduled_dose_id") Long scheduledDoseId,
        @JsonProperty("remaining_count") Integer remainingCount
) {
    public static DeviceSignalResponse of(Long signalId, String outcome) {
        return new DeviceSignalResponse(signalId, outcome, null, null, null);
    }
}