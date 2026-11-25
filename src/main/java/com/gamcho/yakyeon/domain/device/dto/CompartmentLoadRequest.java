package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.device.logic.CompartmentRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/** 칸에 처음 채운 약봉지 수 */
@Getter
public class CompartmentLoadRequest {

    @JsonProperty("loaded_count")
    @NotNull(message = "채운 약봉지 수를 입력해주세요.")
    @Min(value = 1, message = "약봉지는 1개 이상 채워야 합니다.")
    @Max(value = CompartmentRules.MAX_POUCHES_PER_REQUEST, message = "한 번에 채울 수 있는 약봉지는 최대 100개입니다.")
    private Integer loadedCount;
}