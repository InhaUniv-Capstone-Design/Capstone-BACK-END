package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * 비밀번호 변경 요청. 로그인된 상태(Access Token)에서만 호출 가능.
 */
@Getter
public class PasswordChangeRequest {

    @JsonProperty("current_password")
    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    private String currentPassword;

    @JsonProperty("new_password")
    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Pattern(regexp = InputRules.PASSWORD_REGEX, message = InputRules.PASSWORD_MESSAGE)
    private String newPassword;

    @JsonProperty("new_password_confirm")
    @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
    private String newPasswordConfirm;
}