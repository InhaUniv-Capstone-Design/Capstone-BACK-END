package com.gamcho.yakyeon.domain.medication.entity;

import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 처방전 한 장 단위의 묶음 (prescription). 병원별 처방 조회와 약 묶음(조제 봉지)의 기준.
 *
 * 원본 이미지는 기본적으로 저장하지 않는다 (DB 제약: image_retained가 false이면 image_path는 비어 있어야 함).
 * 이미지 보관 기능이 생기기 전까지 이 엔티티는 이미지 경로를 받지 않는다.
 */
@Entity
@Table(name = "prescription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prescription_id")
    private Long prescriptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private Source source;

    @Column(name = "hospital_name", length = 100)
    private String hospitalName;

    @Column(name = "pharmacy_name", length = 100)
    private String pharmacyName;

    @Column(name = "prescribed_date")
    private LocalDate prescribedDate;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "image_retained", nullable = false)
    private boolean imageRetained;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Prescription(Patient patient, Source source, String hospitalName, String pharmacyName,
                        LocalDate prescribedDate, AppUser createdBy) {
        this.patient = patient;
        this.source = source;
        this.hospitalName = hospitalName;
        this.pharmacyName = pharmacyName;
        this.prescribedDate = prescribedDate;
        this.createdBy = createdBy;
        this.imageRetained = false;
        this.imagePath = null;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Source {
        OCR,     // 처방전 촬영으로 등록
        MANUAL   // 직접 입력
    }
}