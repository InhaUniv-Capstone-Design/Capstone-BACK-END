package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 상태 보고에 대한 응답.
 *
 * @param serverTime      서버의 현재 시각. 인터넷 시각(NTP)을 쓰지 못하는 기기가 시계를 맞출 때 쓴다
 * @param scheduleVersion 서버가 가진 최신 일정 버전. 기기가 아는 버전보다 크면 일정을 새로 받아 가야 한다
 */
public record DeviceHeartbeatResponse(
        @JsonProperty("server_time") OffsetDateTime serverTime,
        @JsonProperty("schedule_version") Integer scheduleVersion
) {
}