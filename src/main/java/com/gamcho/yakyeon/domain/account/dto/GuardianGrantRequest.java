package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.Map;

/**
 * 복약자 웹의 "열람 범위 저장" 요청 (POST /guardian-links/grant).
 * consents에는 6개 항목이 모두 true 또는 false로 있어야 한다.
 */
@Getter
public class GuardianGrantRequest {

    @JsonProperty("guardian_login_id")
    @NotBlank(message = "대리인 앱 아이디를 입력해주세요.")
    @Size(max = 30, message = "대리인 앱 아이디가 올바르지 않습니다.")
    private String guardianLoginId;

    @JsonProperty("relationship")
    @Size(max = SharedItemRules.RELATIONSHIP_MAX, message = "관계는 20자 이하여야 합니다.")
    private String relationship;

    @JsonProperty("terms_version_id")
    @NotNull(message = "동의 문구 약관 버전(terms_version_id)을 보내주세요.")
    private Long termsVersionId;

    @JsonProperty("consents")
    @NotNull(message = "열람 범위(consents)를 보내주세요.")
    private Map<String, Boolean> consents;

    @JsonIgnore
    @AssertTrue(message = "consents에는 6개 항목(PERSONAL_INFO, MED_INFO, PHOTO, DOSE_RECORD, DEVICE_EVENT, DUR_INFO)이 모두 true 또는 false로 있어야 합니다.")
    public boolean isConsentsComplete() {
        // consents가 비어 있는 경우는 위의 @NotNull이 먼저 알려 준다
        return consents == null || SharedItemRules.isComplete(consents);
    }
}