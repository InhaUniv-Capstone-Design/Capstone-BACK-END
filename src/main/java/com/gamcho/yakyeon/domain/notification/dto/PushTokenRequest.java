package com.gamcho.yakyeon.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 푸시 알림을 받을 기기의 FCM 토큰 등록. 앱을 설치한 기기마다 하나이고, 앱이 토큰을 새로 받을 때마다 다시 보낸다.
 * 토큰은 알림 경로의 열쇠라서 주소에 넣지 않고 본문으로만 주고받는다.
 */
@Getter
public class PushTokenRequest {

    @JsonProperty("fcm_token")
    @NotBlank(message = "푸시 토큰을 입력해주세요.")
    @Size(min = 20, max = 512, message = "푸시 토큰은 20~512자여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9:_-]+$", message = "푸시 토큰에 사용할 수 없는 문자가 있습니다.")
    private String fcmToken;

    /** 기기에서 알림 권한을 허용했는지. 없으면 허용으로 본다. 꺼져 있으면 알림은 쌓이지만 푸시는 보내지 않는다 */
    @JsonProperty("notification_permitted")
    private Boolean notificationPermitted;
}