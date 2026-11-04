package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Map;

/** 연동 후 열람 범위 변경 요청 (PATCH /guardian-links/{linkId}/shared-items). 6개 항목이 모두 있어야 한다. */
@Getter
public class SharedItemsChangeRequest {

    @JsonProperty("consents")
    @NotNull(message = "열람 범위(consents)를 보내주세요.")
    private Map<String, Boolean> consents;

    @JsonIgnore
    @AssertTrue(message = "consents에는 6개 항목(PERSONAL_INFO, MED_INFO, PHOTO, DOSE_RECORD, DEVICE_EVENT, DUR_INFO)이 모두 true 또는 false로 있어야 합니다.")
    public boolean isConsentsComplete() {
        return consents == null || SharedItemRules.isComplete(consents);
    }
}