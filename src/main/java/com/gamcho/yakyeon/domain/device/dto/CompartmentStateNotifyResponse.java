package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** @param pushed 복약자 본인 앱에 데이터 푸시를 한 대 이상 보냈으면 true (저장된 상태가 없거나, 앱 계정이 없거나, 받을 기기가 없으면 false) */
public record CompartmentStateNotifyResponse(@JsonProperty("pushed") boolean pushed) {
}