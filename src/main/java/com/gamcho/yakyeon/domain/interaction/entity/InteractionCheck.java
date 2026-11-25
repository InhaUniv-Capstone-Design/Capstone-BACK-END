package com.gamcho.yakyeon.domain.interaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 병용금기 검사 한 번 (interaction_check). 약을 추가·수정·삭제할 때마다 복약자 단위로 한 건씩 쌓이고,
 * 화면은 가장 최근의 성공(SUCCESS) 검사 결과를 보여준다. 이전 검사 기록은 이력으로 남는다.
 */
@Entity
@Table(name = "interaction_check")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id")
    private Long checkId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 12)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    /** 이 검사에 쓴 DUR 데이터 회차. DUR 데이터가 없어서 실패한 검사는 null */
    @Column(name = "dur_dataset_id")
    private Long durDatasetId;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private LocalDateTime checkedAt;

    @Builder
    public InteractionCheck(Long patientId, TriggerType triggerType, Status status, Long durDatasetId) {
        this.patientId = patientId;
        this.triggerType = triggerType;
        this.status = status;
        this.durDatasetId = durDatasetId;
    }

    @PrePersist
    protected void onCreate() {
        this.checkedAt = LocalDateTime.now();
    }

    public enum TriggerType {
        MED_ADD, MED_UPDATE, MED_DELETE, DUR_REFRESH
    }

    public enum Status {
        SUCCESS, FAILED
    }
}