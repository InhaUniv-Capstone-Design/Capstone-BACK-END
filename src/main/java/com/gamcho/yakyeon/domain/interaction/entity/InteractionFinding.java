package com.gamcho.yakyeon.domain.interaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 병용금기 검사 결과 한 건 (interaction_finding) = 복약자의 약 두 개와 그 사이의 DUR 규칙.
 * 사유 등은 검사 당시의 내용을 복사해서 저장한다 - DUR 데이터가 나중에 갱신돼도 그 시점에 보여준 내용이 남는다.
 * 등급이나 점수 같은 위험도 필드는 일부러 두지 않는다 (서비스 정책).
 */
@Entity
@Table(name = "interaction_finding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "finding_id")
    private Long findingId;

    @Column(name = "check_id", nullable = false)
    private Long checkId;

    /** 항상 medicationAId < medicationBId (DB 제약) */
    @Column(name = "medication_a_id", nullable = false)
    private Long medicationAId;

    @Column(name = "medication_b_id", nullable = false)
    private Long medicationBId;

    @Column(name = "dur_seq", nullable = false, length = 20)
    private String durSeq;

    @Column(name = "ingredient_a_code", nullable = false, length = 20)
    private String ingredientACode;

    @Column(name = "ingredient_b_code", nullable = false, length = 20)
    private String ingredientBCode;

    @Column(name = "source_type_code", length = 20)
    private String sourceTypeCode;

    @Column(name = "reason_text", nullable = false, columnDefinition = "TEXT")
    private String reasonText;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "source_name", nullable = false, length = 50)
    private String sourceName;

    @Column(name = "source_base_date", nullable = false)
    private LocalDate sourceBaseDate;

    @Builder
    public InteractionFinding(Long checkId, Long medicationAId, Long medicationBId, String durSeq,
                              String ingredientACode, String ingredientBCode, String sourceTypeCode,
                              String reasonText, String remark, String sourceName, LocalDate sourceBaseDate) {
        this.checkId = checkId;
        this.medicationAId = medicationAId;
        this.medicationBId = medicationBId;
        this.durSeq = durSeq;
        this.ingredientACode = ingredientACode;
        this.ingredientBCode = ingredientBCode;
        this.sourceTypeCode = sourceTypeCode;
        this.reasonText = reasonText;
        this.remark = remark;
        this.sourceName = sourceName;
        this.sourceBaseDate = sourceBaseDate;
    }
}