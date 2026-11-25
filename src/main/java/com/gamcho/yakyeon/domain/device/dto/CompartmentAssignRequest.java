package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.device.logic.PouchCounter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/** 약봉지를 넣을 칸 번호. 이 약통의 칸 수를 넘는 번호는 서비스가 한 번 더 확인해서 거절한다 */
@Getter
public class CompartmentAssignRequest {

    @JsonProperty("compartment_no")
    @NotNull(message = "칸 번호를 입력해주세요.")
    @Min(value = 1, message = "칸 번호는 1~3이어야 합니다.")
    @Max(value = 3, message = "칸 번호는 1~3이어야 합니다.")
    private Integer compartmentNo;

    /**
     * 칸에 넣은 약봉지 수 (선택). 보내지 않으면 서버가 처방의 남은 복용 일정으로 계산한다(복용 시간대 수 × 남은 일수).
     * 약국에서 받은 약봉지가 계산과 다르거나 이미 일부를 먹은 뒤에 넣을 때만 보내면 된다.
     */
    @JsonProperty("loaded_count")
    @Min(value = 1, message = "약봉지는 1개 이상 채워야 합니다.")
    @Max(value = PouchCounter.MAX_POUCHES, message = "채울 수 있는 약봉지는 최대 1500개입니다.")
    private Integer loadedCount;
}