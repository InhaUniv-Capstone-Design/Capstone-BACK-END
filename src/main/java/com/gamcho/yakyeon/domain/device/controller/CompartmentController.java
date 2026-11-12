package com.gamcho.yakyeon.domain.device.controller;

import com.gamcho.yakyeon.domain.device.dto.CompartmentAssignRequest;
import com.gamcho.yakyeon.domain.device.dto.CompartmentLoadRequest;
import com.gamcho.yakyeon.domain.device.dto.CompartmentMappingResponse;
import com.gamcho.yakyeon.domain.device.dto.CompartmentRefillRequest;
import com.gamcho.yakyeon.domain.device.service.CompartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 약통 칸 배정 (약봉지를 칸에 넣기, 충전 확인, 추가 충전, 해제). 모두 수정 권한이 필요하다
 * (복약자 본인 또는 읽기·쓰기 권한 보호자). 개수는 알약이 아니라 약봉지 수다.
 */
@RestController
@RequiredArgsConstructor
public class CompartmentController {

    private final CompartmentService compartmentService;

    /** 약봉지(처방)를 약통의 칸에 배정하고 바로 충전을 확인한 상태(ACTIVE)로 만든다 (별도의 충전 확인 단계가 없다) */
    @PostMapping("/prescriptions/{prescriptionId}/compartment")
    public ResponseEntity<CompartmentMappingResponse> assign(
            @AuthenticationPrincipal Long userId,
            @PathVariable("prescriptionId") Long prescriptionId,
            @Valid @RequestBody CompartmentAssignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(compartmentService.assign(userId, prescriptionId, request.getCompartmentNo(), request.getLoadedCount()));
    }

    /** 충전 확인이 안 된 예전 배정(PENDING_LOAD)의 충전을 확인한다. 새로 배정한 칸은 이미 충전 확인된 상태라 이 API가 필요 없다 */
    @PostMapping("/compartment-mappings/{mappingId}/load")
    public ResponseEntity<CompartmentMappingResponse> load(
            @AuthenticationPrincipal Long userId,
            @PathVariable("mappingId") Long mappingId,
            @Valid @RequestBody CompartmentLoadRequest request) {
        return ResponseEntity.ok(compartmentService.load(userId, mappingId, request.getLoadedCount()));
    }

    /** 사용 중인 칸에 약봉지를 더 채움 */
    @PostMapping("/compartment-mappings/{mappingId}/refill")
    public ResponseEntity<CompartmentMappingResponse> refill(
            @AuthenticationPrincipal Long userId,
            @PathVariable("mappingId") Long mappingId,
            @Valid @RequestBody CompartmentRefillRequest request) {
        return ResponseEntity.ok(compartmentService.refill(userId, mappingId, request.getAddedCount()));
    }

    /** 칸 배정 해제 (기록은 남고, 그 칸에 다른 약봉지를 배정할 수 있다) */
    @DeleteMapping("/compartment-mappings/{mappingId}")
    public ResponseEntity<Void> release(
            @AuthenticationPrincipal Long userId,
            @PathVariable("mappingId") Long mappingId) {
        compartmentService.release(userId, mappingId);
        return ResponseEntity.noContent().build();
    }
}