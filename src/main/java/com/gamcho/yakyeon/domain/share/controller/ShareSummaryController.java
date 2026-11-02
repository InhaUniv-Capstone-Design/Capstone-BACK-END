package com.gamcho.yakyeon.domain.share.controller;

import com.gamcho.yakyeon.domain.share.dto.ShareSummaryResponse;
import com.gamcho.yakyeon.domain.share.service.ShareSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 복약 이력 공유 요약 (설정 > 복약 이력 공유). 본인과 연동된 보호자가 쓸 수 있고, 공유한 내용은 저장하지 않는다.
 * from과 to는 함께 보내거나 둘 다 생략한다(생략하면 오늘까지 최근 30일, 최대 90일).
 */
@RestController
@RequiredArgsConstructor
public class ShareSummaryController {

    private final ShareSummaryService shareSummaryService;

    @GetMapping("/patients/{patientId}/share-summary")
    public ResponseEntity<ShareSummaryResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(shareSummaryService.get(userId, patientId, from, to));
    }
}