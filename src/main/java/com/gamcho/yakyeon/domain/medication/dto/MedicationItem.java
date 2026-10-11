package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.medication.entity.PatientMedication;
import com.gamcho.yakyeon.domain.medication.entity.TimingSlot;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 처방에 담기는 약 한 건.
 *
 * - drug_id: 약물명 자동완성에서 고른 약. 있으면 DB 약으로 확정(MATCHED), 없으면 매칭 불가(UNMATCHED)로 등록한다.
 * - input_name: 사용자가 확인한 약 이름. drug_id가 없으면 필수, 있으면 생략해서 약 이름으로 채울 수 있다.
 * - is_prn: 필요 시 복용. true이면 복용 횟수와 복용 시기를 가질 수 없다.
 * - timing_slots: 복용 시기(아침·점심·저녁). 일반 약은 필수.
 * - times_per_day: 하루 복용 횟수. 생략하면 timing_slots 개수로 정해지고, 있으면 timing_slots 개수와 같아야 한다.
 */
@Getter
public class MedicationItem {

    @JsonProperty("drug_id")
    private Long drugId;

    @JsonProperty("input_name")
    @Size(max = 500, message = "약 이름은 500자 이하여야 합니다.")
    private String inputName;

    /** 1회 복용량(정). 생략하면 1 */
    @JsonProperty("dose_per_intake")
    @DecimalMin(value = "0.01", message = "1회 복용량은 0보다 커야 합니다.")
    @DecimalMax(value = "99.99", message = "1회 복용량은 99.99 이하여야 합니다.")
    @Digits(integer = 2, fraction = 2, message = "1회 복용량은 소수점 둘째 자리까지 입력할 수 있습니다.")
    private BigDecimal dosePerIntake;

    /** 필요 시 복용. 생략하면 false */
    @JsonProperty("is_prn")
    private Boolean prn;

    @JsonProperty("times_per_day")
    @Min(value = 1, message = "하루 복용 횟수는 1~3회입니다.")
    @Max(value = 3, message = "하루 복용 횟수는 1~3회입니다.")
    private Short timesPerDay;

    @JsonProperty("timing_slots")
    private List<TimingSlot> timingSlots;

    @JsonProperty("meal_relation")
    private PatientMedication.MealRelation mealRelation;

    @JsonProperty("meal_offset_min")
    @Min(value = 0, message = "식사 전후 시간은 0분 이상이어야 합니다.")
    @Max(value = 240, message = "식사 전후 시간은 240분 이하여야 합니다.")
    private Short mealOffsetMin;

    @JsonProperty("total_days")
    @NotNull(message = "총 투약일수를 입력해주세요.")
    @Min(value = 1, message = "총 투약일수는 1일 이상이어야 합니다.")
    @Max(value = 365, message = "총 투약일수는 365일 이하여야 합니다.")
    private Integer totalDays;

    /** 복용 시작일. 생략하면 오늘. 이미 복용 중인 약(과거)이나 내일부터 먹는 약(미래)도 허용한다 */
    @JsonProperty("start_date")
    private LocalDate startDate;
}