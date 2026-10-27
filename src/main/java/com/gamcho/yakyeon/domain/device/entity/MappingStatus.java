package com.gamcho.yakyeon.domain.device.entity;

/** 칸 배정 상태. DB의 CHECK 제약(PENDING_LOAD, ACTIVE, ENDED)과 같아야 한다. */
public enum MappingStatus {
    PENDING_LOAD,   // 칸은 정했지만 약봉지를 아직 채우지 않음
    ACTIVE,         // 충전이 확인되어 쓰는 중
    ENDED           // 해제되었거나 처방이 끝나서 종료 (기록은 남음)
}