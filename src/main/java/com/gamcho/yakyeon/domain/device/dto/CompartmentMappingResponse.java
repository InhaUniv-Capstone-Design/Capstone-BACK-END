package com.gamcho.yakyeon.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.device.entity.CompartmentMapping;
import com.gamcho.yakyeon.domain.device.entity.MappingStatus;
import com.gamcho.yakyeon.domain.device.logic.CompartmentRules;

import java.time.OffsetDateTime;

/** 칸 배정 한 건. 개수는 알약이 아니라 약봉지 수다. 시각은 한국 시간(+09:00)이다 */
public record CompartmentMappingResponse(
        @JsonProperty("mapping_id") Long mappingId,
        @JsonProperty("device_id") Long deviceId,
        @JsonProperty("compartment_no") int compartmentNo,
        @JsonProperty("prescription_id") Long prescriptionId,
        @JsonProperty("status") MappingStatus status,
        @JsonProperty("loaded_count") Integer loadedCount,
        @JsonProperty("remaining_count") Integer remainingCount,
        @JsonProperty("low_stock") boolean lowStock,
        @JsonProperty("valid_from") OffsetDateTime validFrom,
        @JsonProperty("valid_to") OffsetDateTime validTo
) {
    public static CompartmentMappingResponse from(CompartmentMapping m) {
        return new CompartmentMappingResponse(m.getMappingId(), m.getDeviceId(), m.getCompartmentNo(),
                m.getPrescriptionId(), m.getStatus(), m.getLoadedCount(), m.getRemainingCount(),
                CompartmentRules.isLowStock(m.getStatus().name(), m.getRemainingCount(), m.getLowStockThreshold()),
                kst(m.getValidFrom()), kst(m.getValidTo()));
    }

    private static OffsetDateTime kst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime();
    }
}