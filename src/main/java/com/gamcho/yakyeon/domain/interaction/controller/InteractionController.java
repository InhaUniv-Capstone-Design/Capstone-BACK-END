package com.gamcho.yakyeon.domain.interaction.controller;

import com.gamcho.yakyeon.domain.interaction.dto.InteractionEdgeDetailResponse;
import com.gamcho.yakyeon.domain.interaction.dto.InteractionGraphResponse;
import com.gamcho.yakyeon.domain.interaction.dto.InteractionItemResponse;
import com.gamcho.yakyeon.domain.interaction.dto.InteractionSummaryResponse;
import com.gamcho.yakyeon.domain.interaction.service.InteractionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API 명세 §4 병용금기 (FR-HOME-005, FR-GRAPH-001~005).
 * 복약자별 조회는 접근 가드를 거치고(본인 또는 활성 연동 보호자), 모두 로그인이 필요하다.
 * 응답에는 등급·점수 같은 위험도 필드를 두지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionQueryService interactionQueryService;

    /** 4-1 등재된 병용금기 목록 */
    @GetMapping("/patients/{patientId}/interactions")
    public ResponseEntity<List<InteractionItemResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.ok(interactionQueryService.list(userId, patientId));
    }

    /** 4-1 홈 화면 요약 (복용 중인 약 수, 병용금기 건수, 확인하지 못한 약 수) */
    @GetMapping("/patients/{patientId}/interactions/summary")
    public ResponseEntity<InteractionSummaryResponse> summary(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.ok(interactionQueryService.summary(userId, patientId));
    }

    /** 4-2 약물 관계 그래프 (노드, 엣지, 묶음) */
    @GetMapping("/patients/{patientId}/interaction-graph")
    public ResponseEntity<InteractionGraphResponse> graph(
            @AuthenticationPrincipal Long userId,
            @PathVariable("patientId") Long patientId) {
        return ResponseEntity.ok(interactionQueryService.graph(userId, patientId));
    }

    /** 4-2 엣지 클릭 상세 팝업 (사유, 출처, 기준일, 면책 문구) */
    @GetMapping("/interaction-edges/{durSeq}")
    public ResponseEntity<InteractionEdgeDetailResponse> edgeDetail(@PathVariable("durSeq") String durSeq) {
        return ResponseEntity.ok(interactionQueryService.edgeDetail(durSeq));
    }
}