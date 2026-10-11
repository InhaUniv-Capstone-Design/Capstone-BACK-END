package com.gamcho.yakyeon.domain.medication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.medication.entity.Prescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * 처방 등록 요청 (POST /patients/{patientId}/prescriptions).
 * 처방전 한 장 = 처방 1건 + 약 여러 건. OCR로 인식해서 사용자가 확인한 결과이거나 직접 입력한 값이다.
 *
 * 형식 검증(@Valid)은 여기서, 여러 필드가 얽힌 규칙(필요 시 복용과 복용 시기, 횟수와 시기 개수 등)은
 * PrescriptionService에서 확인한다.
 */
@Getter
public class PrescriptionCreateRequest {

    @JsonProperty("hospital_name")
    @Size(max = 100, message = "병원명은 100자 이하여야 합니다.")
    private String hospitalName;

    @JsonProperty("pharmacy_name")
    @Size(max = 100, message = "약국명은 100자 이하여야 합니다.")
    private String pharmacyName;

    @JsonProperty("prescribed_date")
    private LocalDate prescribedDate;

    /** 등록 방법. 생략하면 MANUAL(직접 입력). OCR 인식 결과를 확인해서 등록하는 경우 OCR */
    @JsonProperty("source")
    private Prescription.Source source;

    @JsonProperty("medications")
    @NotEmpty(message = "약을 1개 이상 입력해주세요.")
    @Size(max = 20, message = "한 번에 등록할 수 있는 약은 20개까지입니다.")
    @Valid
    private List<MedicationItem> medications;
}