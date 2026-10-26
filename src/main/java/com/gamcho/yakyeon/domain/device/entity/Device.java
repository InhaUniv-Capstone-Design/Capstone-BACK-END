package com.gamcho.yakyeon.domain.device.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 스마트 약통 한 대 (device). 복약자 한 명에게 등록된 약통은 하나뿐이고(DB 유니크), 같은 시리얼번호는 동시에
 * 한 복약자에게만 등록된다. 해제하면 행을 지우지 않고 UNREGISTERED로 남긴다 (예전 신호·이벤트가 이 기기를 가리킨다).
 *
 * 기기 비밀값은 원문을 저장하지 않고 해시(secret_hash)만 저장한다. 기기는 신호를 보낼 때마다 시리얼번호와
 * 비밀값으로 인증하며, 서버는 저장된 해시와 비교한다.
 */
@Entity
@Table(name = "device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "serial_no", nullable = false, length = 40)
    private String serialNo;

    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 이 약통을 등록(소유 확인)한 계정 */
    @Column(name = "owner_confirmed_by")
    private Long ownerConfirmedBy;

    /** 약통이 가진 칸 수 (1~3). 칸 배정 때 이 값을 넘는 칸 번호는 DB가 거절한다 */
    @Column(name = "max_compartments", nullable = false)
    private Short maxCompartments;

    /** 열림 신호와 버튼 신호를 한 번의 복용으로 묶는 허용 시간(초). DB 기본값을 쓰고 서버는 읽기만 한다 */
    @Column(name = "pair_window_sec", insertable = false, updatable = false)
    private Short pairWindowSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private DeviceStatus status;

    @Column(name = "firmware_version", length = 20)
    private String firmwareVersion;

    @Column(name = "ir_sensor_ok")
    private Boolean irSensorOk;

    @Column(name = "button_ok")
    private Boolean buttonOk;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    /** 서버가 가진 최신 일정 버전 (DB 기본값에서 시작, 일정이 바뀌면 올라간다) */
    @Column(name = "schedule_version", insertable = false, updatable = false)
    private Integer scheduleVersion;

    /** 기기가 마지막으로 받아 갔다고 보고한 일정 버전 */
    @Column(name = "schedule_synced_ver", nullable = false)
    private Integer scheduleSyncedVer;

    @Column(name = "schedule_synced_at")
    private OffsetDateTime scheduleSyncedAt;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "unregistered_at")
    private OffsetDateTime unregisteredAt;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "online_changed_at")
    private OffsetDateTime onlineChangedAt;

    @Builder
    public Device(String serialNo, String secretHash, Long patientId, Long ownerConfirmedBy,
                  Short maxCompartments, OffsetDateTime registeredAt) {
        this.serialNo = serialNo;
        this.secretHash = secretHash;
        this.patientId = patientId;
        this.ownerConfirmedBy = ownerConfirmedBy;
        this.maxCompartments = maxCompartments;
        this.registeredAt = registeredAt;
        this.status = DeviceStatus.REGISTERED;
        this.scheduleSyncedVer = 0;
        this.online = false;
    }

    /** 기기가 상태를 보고했다 (마지막 접속 시각 갱신, 온라인으로 표시, 보고된 항목만 반영) */
    public void markSeen(OffsetDateTime now, String firmwareVersion, Boolean irSensorOk, Boolean buttonOk) {
        this.lastSeenAt = now;
        if (!this.online) {
            this.online = true;
            this.onlineChangedAt = now;
        }
        if (firmwareVersion != null) {
            this.firmwareVersion = firmwareVersion;
        }
        if (irSensorOk != null) {
            this.irSensorOk = irSensorOk;
        }
        if (buttonOk != null) {
            this.buttonOk = buttonOk;
        }
    }

    /** 기기가 받아 간 일정 버전을 기록한다 (같은 버전이면 바꾸지 않는다) */
    public void reportScheduleSynced(Integer version, OffsetDateTime now) {
        if (version != null && !version.equals(this.scheduleSyncedVer)) {
            this.scheduleSyncedVer = version;
            this.scheduleSyncedAt = now;
        }
    }

    /** 등록 해제. 해제된 기기는 신호를 보낼 수 없고(인증 실패) 같은 시리얼번호를 다시 등록할 수 있다 */
    public void unregister(OffsetDateTime now) {
        this.status = DeviceStatus.UNREGISTERED;
        this.unregisteredAt = now;
        if (this.online) {
            this.online = false;
            this.onlineChangedAt = now;
        }
    }
}