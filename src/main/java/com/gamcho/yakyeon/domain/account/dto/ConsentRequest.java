package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 위임 동의/거부 요청.
 * - GRANT: verification_id 필수. 만 14세 미만이면 legal_rep_name도 필수.
 * - REJECT: 인증 없이 가능 (대기 중인 요청을 닫는 동작).
 */
@Getter
public class ConsentRequest {

    /** REVOKE/SCOPE_CHANGE 같은 다른 이력 종류는 이 API로 만들 수 없게 별도 enum으로 제한 */
    public enum Decision {
        GRANT, REJECT
    }

    @JsonProperty("action")
    @NotNull(message = "action(GRANT 또는 REJECT)이 필요합니다.")
    private Decision action;

    @JsonProperty("verification_id")
    private Long verificationId;

    @JsonProperty("legal_rep_name")
    @Size(max = 50, message = "법정대리인 이름은 50자 이하여야 합니다.")
    private String legalRepName;
}