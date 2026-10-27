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
 * 약통 칸 배정 (compartment_mapping) = 약봉지(처방) 하나가 약통의 한 칸에 들어가는 관계.
 * 약마다가 아니라 약봉지 단위다. 한 칸에는 동시에 약봉지 하나만, 한 약봉지는 동시에 한 칸에만 배정된다 (DB 유니크).
 * 해제되거나 처방이 끝나면 지우지 않고 ENDED로 남긴다 (복용 이벤트가 이 배정을 가리킨다).
 *
 * 개수는 알약이 아니라 "약봉지 수"다. 충전할 때 채운 수(loaded)와 남은 수(remaining)를 따로 센다.
 */
@Entity
@Table(name = "compartment_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompartmentMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "compartment_no", nullable = false)
    private Short compartmentNo;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private MappingStatus status;

    /** 충전한 약봉지 수 (충전 확인 전에는 비어 있음) */
    @Column(name = "loaded_count")
    private Integer loadedCount;

    @Column(name = "remaining_count")
    private Integer remainingCount;

    /** 남은 수가 이 값 이하면 "약이 얼마 안 남았다"고 본다. DB 기본값을 쓰고 서버는 읽기만 한다 */
    @Column(name = "low_stock_threshold", insertable = false, updatable = false)
    private Integer lowStockThreshold;

    @Column(name = "low_stock_notified", nullable = false)
    private boolean lowStockNotified;

    /** 칸 배정과 충전을 확인한 계정 */
    @Column(name = "confirmed_by_user_id")
    private Long confirmedByUserId;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @Builder
    public CompartmentMapping(Long deviceId, Long patientId, Short compartmentNo, Long prescriptionId,
                              Long confirmedByUserId) {
        this.deviceId = deviceId;
        this.patientId = patientId;
        this.compartmentNo = compartmentNo;
        this.prescriptionId = prescriptionId;
        this.confirmedByUserId = confirmedByUserId;
        this.status = MappingStatus.PENDING_LOAD;
        this.lowStockNotified = false;
    }

    /** 처음 충전을 확인한다 (PENDING_LOAD → ACTIVE). 이 시각부터 이 칸의 신호를 이 약봉지로 본다 */
    public void load(int count, Long userId, OffsetDateTime now) {
        this.status = MappingStatus.ACTIVE;
        this.loadedCount = count;
        this.remainingCount = count;
        this.validFrom = now;
        this.confirmedByUserId = userId;
        this.lowStockNotified = false;
    }

    /** 약봉지를 더 채운다. 남은 수가 기준을 넘으면 "얼마 안 남음" 알림을 다시 보낼 수 있게 초기화한다 */
    public void refill(int added, Long userId) {
        this.loadedCount = this.loadedCount + added;
        this.remainingCount = this.remainingCount + added;
        this.confirmedByUserId = userId;
        if (this.lowStockThreshold != null && this.remainingCount > this.lowStockThreshold) {
            this.lowStockNotified = false;
        }
    }

    /**
     * 배정을 끝낸다. DB는 종료 상태에 시작 시각과 충전량이 있어야 한다고 정해서,
     * 충전하지 않은 배정에는 지금 시각과 0을 채워 넣는다 (DB의 탈퇴·처방 종료 처리와 같은 방식).
     */
    public void end(OffsetDateTime now) {
        this.status = MappingStatus.ENDED;
        this.validTo = now;
        if (this.validFrom == null) {
            this.validFrom = now;
        }
        if (this.loadedCount == null) {
            this.loadedCount = 0;
        }
    }
}