package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 복약자 등록 요청.
 * 호출한 계정이 PATIENT면 본인을 복약자로 등록하고, GUARDIAN이면 앱 없는
 * 피보호자를 새로 등록한다 (어느 쪽인지는 요청에 안 담고, 서버가 토큰의
 * account_type을 보고 판단한다).
 */
@Getter
public class PatientRegisterRequest {

    @JsonProperty("name")
    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @JsonProperty("birth_date")
    @NotNull(message = "생년월일을 입력해주세요.")
    @Past(message = "생년월일이 올바르지 않습니다.")
    private LocalDate birthDate;

    /** 하이픈 없이 숫자만 (예: 01012345678). 서버에서 암호화해서 저장하고 평문은 남기지 않음 */
    @JsonProperty("phone")
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다. (예: 01012345678)")
    private String phone;
}