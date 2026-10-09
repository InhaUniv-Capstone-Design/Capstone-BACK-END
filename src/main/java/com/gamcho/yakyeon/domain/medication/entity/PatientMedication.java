package com.gamcho.yakyeon.domain.medication.entity;

import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.drug.entity.Drug;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 복약자가 복용 중인 약 (patient_medication).
 * 의약품 DB에서 못 찾은 약도 "매칭 불가(UNMATCHED)"로 등록할 수 있다.
 *
 * 삭제는 실제로 지우지 않고 deleted_at으로 표시한다 (복용 기록이 이 행을 참조하기 때문).
 * 그래서 조회할 때는 항상 deleted_at이 비어 있는 행만 대상으로 해야 한다.
 *
 * DB 제약을 엔티티가 스스로 어기지 않게 한다:
 *  - match_status는 drug가 있으면 MATCHED, 없으면 UNMATCHED로 자동 결정 (DB CHECK: 둘이 반드시 일치)
 *  - 필요 시 복용(PRN) 약은 하루 복용 횟수와 복용 시기를 가질 수 없고, 일반 약은 복용 횟수가 필수
 *    (DB CHECK ck_med_prn_times + 트리거 guard_prn_medication). 어기면 DB 에러(500)가 나기 전에 여기서 막는다
 *  - 식사 관계 없이 "식후 몇 분"만 있을 수 없다 (DB CHECK ck_med_meal_offset)
 *  - end_date는 DB가 계산하는 컬럼(시작일 + 투약일수 - 1)이라 읽기 전용이고, 저장 직후에도 쓸 수 있게 자바에서도 계산해 준다
 */
@Entity
@Table(name = "patient_medication")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medication_id")
    private Long medicationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** 어느 처방전에서 왔는지. 직접 입력이면 비어 있을 수 있다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id")
    private Prescription prescription;

    /** 매칭된 의약품. 매칭 불가면 비어 있다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drug_id")
    private Drug drug;

    /** 사용자가 확인한 약 이름 원문 (DB 컬럼이 TEXT) */
    @Column(name = "input_name", nullable = false, columnDefinition = "TEXT")
    private String inputName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 10)
    private MatchStatus matchStatus;

    /** 1회 복용량(정) */
    @Column(name = "dose_per_intake", nullable = false, precision = 5, scale = 2)
    private BigDecimal dosePerIntake;

    /** 필요 시 복용(PRN) 약 여부. true이면 복용 횟수·복용 시기·예정 복용을 만들 수 없다 */
    @Column(name = "is_prn", nullable = false)
    private boolean prn;

    /** 하루 복용 횟수 (DB 제약: 1~3, PRN이면 반드시 비어 있음). DB 컬럼이 SMALLINT라 Short */
    @Column(name = "times_per_day")
    private Short timesPerDay;

    /** 식전/식후/식사와 함께. 모르면 비어 있음 */
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_relation", length = 12)
    private MealRelation mealRelation;

    /** 식사 전·후 몇 분 (식사 관계가 있을 때만 값을 가질 수 있음) */
    @Column(name = "meal_offset_min")
    private Short mealOffsetMin;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** DB가 계산하는 생성 컬럼이라 저장·수정 대상에서 제외. 값은 getEndDate()로 읽는다 */
    @Column(name = "end_date", insertable = false, updatable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 아침·점심·저녁 중 언제 복용하는지. 약과 함께 저장·삭제된다 */
    @OneToMany(mappedBy = "medication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MedicationTiming> timings = new ArrayList<>();

    @Builder
    public PatientMedication(Patient patient, Prescription prescription, Drug drug, String inputName,
                             BigDecimal dosePerIntake, boolean prn, Short timesPerDay,
                             MealRelation mealRelation, Short mealOffsetMin, Integer totalDays,
                             LocalDate startDate, AppUser createdBy, Collection<TimingSlot> timingSlots) {
        validateSchedule(prn, timesPerDay, timingSlots, mealRelation, mealOffsetMin);
        this.patient = patient;
        this.prescription = prescription;
        this.inputName = inputName;
        this.dosePerIntake = dosePerIntake != null ? dosePerIntake : BigDecimal.ONE;
        this.prn = prn;
        this.timesPerDay = timesPerDay;
        this.mealRelation = mealRelation;
        this.mealOffsetMin = mealOffsetMin;
        this.totalDays = totalDays;
        this.startDate = startDate;
        this.createdBy = createdBy;
        changeDrug(drug);
        replaceTimings(timingSlots);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 약 정보 수정. 매칭 상태는 drug 유무로 다시 결정된다.
     *
     * 일반 약 ↔ 필요 시 복용(PRN) 전환은 이 메서드로 할 수 없다 (IllegalArgumentException).
     * 전환은 DB 트리거(guard_prn_medication) 때문에 저장 순서가 중요하다 - Hibernate는 한 번의 저장에서
     * "새 행 INSERT → 기존 행 UPDATE" 순서로 실행해서, 시간대를 먼저 지우거나 is_prn을 먼저 바꾸는 순서를
     * 보장하지 못한다. 전환이 필요해지면 서비스에서 단계마다 flush하는 별도 메서드로 처리한다.
     */
    public void update(Drug drug, String inputName, BigDecimal dosePerIntake, boolean prn, Short timesPerDay,
                       MealRelation mealRelation, Short mealOffsetMin, Integer totalDays, LocalDate startDate,
                       Collection<TimingSlot> timingSlots) {
        if (prn != this.prn) {
            throw new IllegalArgumentException("필요 시 복용과 일반 복용 사이의 전환은 update()로 할 수 없습니다.");
        }
        validateSchedule(prn, timesPerDay, timingSlots, mealRelation, mealOffsetMin);
        this.inputName = inputName;
        this.dosePerIntake = dosePerIntake != null ? dosePerIntake : BigDecimal.ONE;
        this.timesPerDay = timesPerDay;
        this.mealRelation = mealRelation;
        this.mealOffsetMin = mealOffsetMin;
        this.totalDays = totalDays;
        this.startDate = startDate;
        changeDrug(drug);
        replaceTimings(timingSlots);
    }

    /** DB 제약을 저장 전에 미리 검사한다. 실제 입력 검증은 DTO(400)에서 먼저 걸러야 하고, 여기는 마지막 방어선 */
    private static void validateSchedule(boolean prn, Short timesPerDay, Collection<TimingSlot> slots,
                                         MealRelation mealRelation, Short mealOffsetMin) {
        boolean hasSlots = slots != null && !slots.isEmpty();
        if (prn && (timesPerDay != null || hasSlots)) {
            throw new IllegalArgumentException("필요 시 복용 약은 복용 횟수와 복용 시기를 가질 수 없습니다.");
        }
        if (!prn && timesPerDay == null) {
            throw new IllegalArgumentException("일반 약은 하루 복용 횟수가 필요합니다.");
        }
        if (mealOffsetMin != null && mealRelation == null) {
            throw new IllegalArgumentException("식사 전·후 시간은 식사 관계와 함께만 지정할 수 있습니다.");
        }
    }

    /** 의약품 매칭 변경. DB 제약(MATCHED ⟺ drug_id 있음)을 항상 만족시킨다 */
    private void changeDrug(Drug drug) {
        this.drug = drug;
        this.matchStatus = drug != null ? MatchStatus.MATCHED : MatchStatus.UNMATCHED;
    }

    /** 복용 시기를 주어진 목록과 같아지게 맞춘다 (중복은 무시, 빠진 건 제거, 새 건 추가) */
    public void replaceTimings(Collection<TimingSlot> slots) {
        Set<TimingSlot> wanted = slots == null ? new LinkedHashSet<>() : new LinkedHashSet<>(slots);
        this.timings.removeIf(t -> !wanted.contains(t.getTimingSlot()));
        Set<TimingSlot> existing = new LinkedHashSet<>();
        for (MedicationTiming t : this.timings) {
            existing.add(t.getTimingSlot());
        }
        for (TimingSlot slot : wanted) {
            if (!existing.contains(slot)) {
                this.timings.add(new MedicationTiming(this, slot));
            }
        }
    }

    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 복용 종료일 = 시작일 + 투약일수 - 1. DB 생성 컬럼과 같은 식이라 저장 직후에도 사용할 수 있다 */
    public LocalDate getEndDate() {
        if (startDate == null || totalDays == null) {
            return endDate;
        }
        return startDate.plusDays(totalDays - 1L);
    }

    public enum MealRelation {
        BEFORE_MEAL,  // 식전
        AFTER_MEAL,   // 식후
        WITH_MEAL     // 식사와 함께
    }

    public enum MatchStatus {
        MATCHED,    // 의약품 DB와 매칭됨
        UNMATCHED   // 매칭 불가 ("확인할 수 없음"으로 표시)
    }
}