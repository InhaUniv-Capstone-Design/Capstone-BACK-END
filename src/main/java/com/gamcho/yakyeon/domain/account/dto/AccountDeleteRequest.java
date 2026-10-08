package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 계정 삭제(탈퇴) 요청. 비밀번호 재확인을 요구해서, 세션이 탈취된 상태에서
 * 버튼 한 번으로 탈퇴 처리되는 걸 막는다.
 */
@Getter
public class AccountDeleteRequest {

    @JsonProperty("password")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
}