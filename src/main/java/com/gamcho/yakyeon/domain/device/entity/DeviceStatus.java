package com.gamcho.yakyeon.domain.device.entity;

/** 약통 등록 상태. DB의 CHECK 제약(REGISTERED, UNREGISTERED)과 같아야 한다. */
public enum DeviceStatus {
    REGISTERED,
    UNREGISTERED
}