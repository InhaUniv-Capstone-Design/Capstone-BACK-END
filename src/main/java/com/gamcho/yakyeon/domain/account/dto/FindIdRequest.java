package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 아이디 찾기 요청 POST /auth/find-id.
 * 이름과 휴대폰 번호만으로는 찾아주지 않는다 - 그 번호가 지금 내 손에 있다는 문자 인증(가입 때와 같은 방식)을 먼저 마쳐야 하고,
 * 그때 받은 증표(verification_id, verification_token)를 같이 보낸다.
 */
@Getter
public class FindIdRequest {

    @JsonProperty("name")
    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    /** 하이픈 없이 숫자만 (예: 01012345678). 문자 인증을 받은 번호와 같아야 한다 */
    @JsonProperty("phone")
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    @Pattern(regexp = InputRules.PHONE_REGEX, message = InputRules.PHONE_MESSAGE)
    private String phone;

    /** POST /auth/phone-verifications 응답의 verification_id */
    @JsonProperty("verification_id")
    @NotNull(message = "휴대폰 인증을 완료해주세요.")
    private Long verificationId;

    /** POST /auth/phone-verifications/{id}/confirm 성공 응답의 verification_token */
    @JsonProperty("verification_token")
    @NotBlank(message = "휴대폰 인증을 완료해주세요.")
    @Size(max = 300, message = "인증 정보가 올바르지 않습니다.")
    private String verificationToken;
}