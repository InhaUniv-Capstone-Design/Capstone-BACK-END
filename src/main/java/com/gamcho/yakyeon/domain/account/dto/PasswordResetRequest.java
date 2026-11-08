package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 비밀번호 재설정 요청 POST /auth/password-reset (로그인 없이 호출).
 * 아이디와 휴대폰 번호가 맞는 것만으로는 바꿔주지 않는다 - 그 번호가 지금 내 손에 있다는 문자 인증(가입 때와 같은 방식)을
 * 먼저 마쳐야 하고, 그때 받은 증표(verification_id, verification_token)를 같이 보낸다.
 * 아이디와 번호는 알아내기 쉬워서(보호자는 복약자의 번호를 알고, 아이디는 가입 가능 여부 조회로 확인된다),
 * 숫자만 맞춰 주면 남의 계정과 건강 정보를 가져갈 수 있기 때문이다.
 */
@Getter
public class PasswordResetRequest {

    @JsonProperty("login_id")
    @NotBlank(message = "아이디를 입력해주세요.")
    @Pattern(regexp = InputRules.LOGIN_ID_REGEX, message = InputRules.LOGIN_ID_MESSAGE)
    private String loginId;

    /** 하이픈 없이 숫자만 (예: 01012345678). 문자 인증을 받은 번호이자 이 계정에 등록된 번호여야 한다 */
    @JsonProperty("phone")
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    @Pattern(regexp = InputRules.PHONE_REGEX, message = InputRules.PHONE_MESSAGE)
    private String phone;

    @JsonProperty("verification_id")
    @NotNull(message = "휴대폰 인증을 완료해주세요.")
    private Long verificationId;

    @JsonProperty("verification_token")
    @NotBlank(message = "휴대폰 인증을 완료해주세요.")
    @Size(max = 300, message = "인증 정보가 올바르지 않습니다.")
    private String verificationToken;

    @JsonProperty("new_password")
    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Pattern(regexp = InputRules.PASSWORD_REGEX, message = InputRules.PASSWORD_MESSAGE)
    private String newPassword;

    @JsonProperty("new_password_confirm")
    @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
    private String newPasswordConfirm;
}