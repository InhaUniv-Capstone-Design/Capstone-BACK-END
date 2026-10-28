package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** 푸시 토큰 해제 (로그아웃할 때 이 기기로 알림이 오지 않게 한다) */
@Getter
public class PushTokenDeleteRequest {

    @JsonProperty("fcm_token")
    @NotBlank(message = "푸시 토큰을 입력해주세요.")
    @Size(min = 20, max = 512, message = "푸시 토큰은 20~512자여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9:_-]+$", message = "푸시 토큰에 사용할 수 없는 문자가 있습니다.")
    private String fcmToken;
}