package com.gamcho.yakyeon.domain.dose.controller;

import com.gamcho.yakyeon.domain.dose.dto.DoseResponse;
import com.gamcho.yakyeon.domain.dose.dto.DoseTakenRequest;
import com.gamcho.yakyeon.domain.dose.service.DoseRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱에서 복약 기록·정정 (복약 알림 팝업 "선택 약물 복약 완료", 정정 화면 "먹었어요 / 안 먹었어요").
 * 수정 권한이 필요하다 (본인 또는 읽기·쓰기 권한 보호자). 응답은 기록 뒤의 예정 한 건이다.
 */
@RestController
@RequiredArgsConstructor
public class DoseRecordController {

    private final DoseRecordService doseRecordService;

    /** 먹었어요. 본문(taken_at)은 선택이고, 없으면 지금 먹은 것으로 기록한다 */
    @PostMapping("/doses/{doseId}/taken")
    public ResponseEntity<DoseResponse> taken(
            @AuthenticationPrincipal Long userId,
            @PathVariable("doseId") Long doseId,
            @RequestBody(required = false) DoseTakenRequest request) {
        return ResponseEntity.ok(doseRecordService.taken(userId, doseId, request == null ? null : request.getTakenAt()));
    }

    /** 안 먹었어요. 마감 전이면 다시 예정으로, 마감 후면 누락으로 바뀐다 */
    @PostMapping("/doses/{doseId}/not-taken")
    public ResponseEntity<DoseResponse> notTaken(
            @AuthenticationPrincipal Long userId,
            @PathVariable("doseId") Long doseId) {
        return ResponseEntity.ok(doseRecordService.notTaken(userId, doseId));
    }
}