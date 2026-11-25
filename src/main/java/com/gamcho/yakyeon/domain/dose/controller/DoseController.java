package com.gamcho.yakyeon.domain.dose.controller;

import com.gamcho.yakyeon.domain.dose.dto.DoseResponse;
import com.gamcho.yakyeon.domain.dose.dto.HomeSummaryResponse;
import com.gamcho.yakyeon.domain.dose.entity.DoseStatus;
import com.gamcho.yakyeon.domain.dose.service.DoseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 복용 일정·기록 조회 (REST API 명세 §6, FR-SCHEDULE / FR-HOME-009).
 * 모두 접근 가드를 거치고(본인 또는 활성 연동 보호자), 로그인이 필요하다. 날짜는 yyyy-MM-dd(한국 날짜)다.
 */
@RestController
@RequiredArgsConstructor
public class DoseController {

    private final DoseQueryService doseQueryService;

    /** 일정 목록 (오늘의 일정, 주간 일정). date 하나 또는 from~to(최대 31일). 아무것도 없으면 오늘 */
    @GetMapping("/patients/{patientId}/doses")
    public ResponseEntity<List<DoseResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(doseQueryService.list(userId, patientId, date, from, to));
    }

    /** 복용 기록(복용 알림 내역). 판정이 끝난 예정만, 최근 것부터. 기본 최근 14일, 최대 90일 */
    @GetMapping("/patients/{patientId}/dose-history")
    public ResponseEntity<List<DoseResponse>> history(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "status", required = false) DoseStatus status) {
        return ResponseEntity.ok(doseQueryService.history(userId, patientId, from, to, status));
    }

    /** 홈 화면의 오늘 복약 현황 */
    @GetMapping("/patients/{patientId}/home-summary")
    public ResponseEntity<HomeSummaryResponse> homeSummary(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.ok(doseQueryService.homeSummary(userId, patientId));
    }
}