package com.gamcho.yakyeon.domain.medication.controller;

import com.gamcho.yakyeon.domain.drug.dto.DrugSuggestionResponse;
import com.gamcho.yakyeon.domain.drug.service.DrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FR-MEDINFO-001 약물명 자동완성. 로그인 필요(SecurityConfig 공개 목록에 없음).
 * 약물 마스터는 공공 데이터라 특정 복약자의 데이터가 아니므로 복약자 접근 가드는 거치지 않는다.
 * GET /medications/{id} 같은 경로가 생겨도 이 구체적인 경로가 먼저 선택된다.
 */
@RestController
@RequestMapping("/medications")
@RequiredArgsConstructor
public class MedicationSearchController {

    private final DrugService drugService;

    @GetMapping("/autocomplete")
    public ResponseEntity<List<DrugSuggestionResponse>> autocomplete(
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(drugService.autocomplete(q, limit));
    }
}