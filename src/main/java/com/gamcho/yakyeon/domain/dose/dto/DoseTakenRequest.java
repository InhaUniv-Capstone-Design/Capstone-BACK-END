package com.gamcho.yakyeon.domain.dose.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * "먹었어요" 요청. taken_at은 실제로 복용한 시각이고, 없으면 지금이다.
 * 예: {"taken_at": "2026-10-26T08:10:00+09:00"}. 정상/지연은 이 시각으로 서버가 정한다.
 */
@Getter
public class DoseTakenRequest {

    @JsonProperty("taken_at")
    private OffsetDateTime takenAt;
}