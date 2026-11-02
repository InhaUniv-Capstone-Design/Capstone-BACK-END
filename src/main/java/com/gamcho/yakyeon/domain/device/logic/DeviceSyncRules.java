package com.gamcho.yakyeon.domain.device.logic;

/**
 * 약통이 서버의 최신 복약 일정을 받았는지 판단하는 규칙 (스프링·DB 없는 순수 로직).
 *
 * 서버는 일정이 바뀌면 일정 버전(device.schedule_version)을 올리고, 약통이 일정을 받으면 받은 버전
 * (device.schedule_synced_ver)을 보고한다. 약통이 받은 버전이 서버의 최신 버전 이상이면 최신 일정을 받은 것이다
 * ("서버 버전이 기기 버전보다 크면 새로 받아 가야 한다"는 상태 보고 응답의 규칙과 같은 기준이다).
 * 값이 비어 있으면 0으로 본다(아직 일정을 한 번도 주고받지 않은 약통).
 */
public final class DeviceSyncRules {

    private DeviceSyncRules() {
    }

    public static boolean isInSync(Integer serverVersion, Integer deviceVersion) {
        int server = serverVersion == null ? 0 : serverVersion;
        int device = deviceVersion == null ? 0 : deviceVersion;
        return server <= device;
    }
}