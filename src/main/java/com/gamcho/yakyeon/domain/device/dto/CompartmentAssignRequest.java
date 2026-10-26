package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
}