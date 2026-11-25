package com.gamcho.yakyeon.domain.schedule.controller;

import com.gamcho.yakyeon.domain.schedule.dto.ScheduleSettingRequest;
import com.gamcho.yakyeon.domain.schedule.dto.ScheduleSettingResponse;
import com.gamcho.yakyeon.domain.schedule.service.ScheduleSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 처방(약봉지)별 복용 시각 설정 (약봉지 등록 뒤 "복용시간 알림 설정" 화면, 설정에서 수정).
 * 약마다 시각을 받지 않고 처방 단위로 아침·점심·저녁 시각을 한 번 정한다.
 * 변경에는 수정 권한이 필요하다 (본인 또는 읽기·쓰기 권한 보호자).
 */
@RestController
@RequiredArgsConstructor
public class ScheduleSettingController {

    private final ScheduleSettingService scheduleSettingService;

    @GetMapping("/prescriptions/{prescriptionId}/schedule-settings")
    public ResponseEntity<ScheduleSettingResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable("prescriptionId") Long prescriptionId) {
        return ResponseEntity.ok(scheduleSettingService.get(userId, prescriptionId));
    }

    /** 시각 전체를 정한다. 바뀐 시간대만 새 버전이 만들어지고, 이미 지난 복용 기록은 그대로다 */
    @PutMapping("/prescriptions/{prescriptionId}/schedule-settings")
    public ResponseEntity<ScheduleSettingResponse> put(
            @AuthenticationPrincipal Long userId,
            @PathVariable("prescriptionId") Long prescriptionId,
            @Valid @RequestBody ScheduleSettingRequest request) {
        return ResponseEntity.ok(scheduleSettingService.put(userId, prescriptionId, request));
    }
}