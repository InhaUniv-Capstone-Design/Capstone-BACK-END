package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * 약통 등록 요청. 시리얼번호와 비밀값은 기기에 붙은 라벨·QR에 적혀 있는 값이다.
 * 비밀값은 서버가 해시로만 저장하고 응답이나 로그에 절대 다시 내보내지 않는다.
 */
@Getter
public class DeviceRegisterRequest {

    @JsonProperty("serial_no")
    @NotBlank(message = "약통 시리얼번호를 입력해주세요.")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{3,39}$",
            message = "시리얼번호는 영문, 숫자, 점, 밑줄, 하이픈으로 4~40자여야 합니다.")
    private String serialNo;

    @JsonProperty("secret")
    @NotBlank(message = "약통 비밀값을 입력해주세요.")
    @Pattern(regexp = "^[A-Za-z0-9._~+/=-]{32,128}$",
            message = "약통 비밀값은 32~128자의 영문, 숫자, 특수문자(._~+/=-)여야 합니다.")
    private String secret;

    /** 약통의 칸 수. 없으면 3칸 */
    @JsonProperty("max_compartments")
    @Min(value = 1, message = "칸 수는 1~3이어야 합니다.")
    @Max(value = 3, message = "칸 수는 1~3이어야 합니다.")
    private Integer maxCompartments;
}