package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 약통 화면(칸별 현황). compartments는 항상 약통의 칸 수만큼 1번부터 순서대로 들어 있다.
 * 개수는 알약이 아니라 약봉지 수다.
 */
public record PillboxResponse(
        @JsonProperty("device") DeviceSummary device,
        @JsonProperty("compartments") List<Compartment> compartments
) {
    public record DeviceSummary(
            @JsonProperty("device_id") Long deviceId,
            @JsonProperty("serial_no") String serialNo,
            @JsonProperty("is_online") boolean online,
            @JsonProperty("max_compartments") int maxCompartments
    ) {
    }

    /** state: EMPTY(비어 있음) / PENDING_LOAD(칸은 정했고 아직 채우지 않음) / ACTIVE(사용 중) */
    public enum State {
        EMPTY, PENDING_LOAD, ACTIVE
    }

    /**
     * @param pouchPresent 약통이 보고한 "약봉지가 들어 있는지" (보고가 없으면 null)
     * @param alertActive  약통이 지금 이 칸에서 알림(부저, LED)을 울리는 중인지 (보고가 없으면 null)
     */
    public record Compartment(
            @JsonProperty("compartment_no") int compartmentNo,
            @JsonProperty("state") State state,
            @JsonProperty("mapping_id") Long mappingId,
            @JsonProperty("prescription_id") Long prescriptionId,
            @JsonProperty("hospital_name") String hospitalName,
            @JsonProperty("medications") List<Medication> medications,
            @JsonProperty("loaded_count") Integer loadedCount,
            @JsonProperty("remaining_count") Integer remainingCount,
            @JsonProperty("low_stock") boolean lowStock,
            @JsonProperty("pouch_present") Boolean pouchPresent,
            @JsonProperty("alert_active") Boolean alertActive
    ) {
    }

    public record Medication(
            @JsonProperty("medication_id") Long medicationId,
            @JsonProperty("drug_name") String drugName
    ) {
    }
}