package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 프로필 수정. 지금은 이름만 바꿀 수 있다.
 * 생년월일은 "만 14세 미만이면 법정대리인 동의가 필요하다"는 규칙의 기준이라서 가입 뒤에 고칠 수 없게 했다
 * (고칠 수 있으면 규칙을 우회할 수 있다). 휴대폰 번호는 문자 인증이 필요해서 별도 흐름으로 정한다.
 * 허용하지 않는 항목(birth_date, phone 등)을 보내면 조용히 무시하지 않고 400으로 알려 준다.
 */
@Getter
public class ProfileUpdateRequest {

    @JsonProperty("name")
    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @JsonIgnore
    private final List<String> unknownFields = new ArrayList<>();

    @JsonAnySetter
    public void captureUnknown(String key, Object value) {
        unknownFields.add(key);
    }

    @JsonIgnore
    @AssertTrue(message = "변경할 수 없는 항목이 포함되어 있습니다. 이름만 수정할 수 있습니다.")
    public boolean isOnlyAllowedFields() {
        return unknownFields.isEmpty();
    }
}