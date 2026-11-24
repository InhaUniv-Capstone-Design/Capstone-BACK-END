package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 중계 서버가 약통의 칸 상태(pouch_present)를 DB에 저장한 직후 보내는 알림. 예: {"compartment_no": 1}
 * 상태 값은 받지 않는다 - 서버가 방금 저장된 값을 DB에서 직접 확인한다. 칸 번호가 올바른지는 호출자 확인 뒤에 서비스가 검사한다.
 */
@Getter
public class CompartmentStateNotifyRequest {

    @JsonProperty("compartment_no")
    private Integer compartmentNo;
}