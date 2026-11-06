package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.ratelimit.FixedWindowCounter;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.account.consent.ConsentGuard;
import com.gamcho.yakyeon.domain.drug.dto.DrugSuggestionResponse;
import com.gamcho.yakyeon.domain.drug.service.DrugService;
import com.gamcho.yakyeon.domain.medication.dto.OcrRecognizeResponse;
import com.gamcho.yakyeon.domain.medication.dto.RecognizedMedication;
import com.gamcho.yakyeon.domain.medication.ocr.DrugNameMatcher;
import com.gamcho.yakyeon.domain.medication.ocr.ImageSignature;
import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;
import com.gamcho.yakyeon.domain.medication.ocr.OcrUnavailableException;
import com.gamcho.yakyeon.domain.medication.ocr.PrescriptionOcrClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 처방전 이미지 OCR (FR-ADD). 이미지를 읽어 약 목록 초안을 돌려줄 뿐 아무것도 저장하지 않는다 -
 * 사용자가 초안을 확인한 뒤 처방 등록 API를 따로 호출한다. 이미지도 저장하지 않는다.
 *
 * 외부 OCR(CLOVA)은 호출마다 비용이 들고 월 무료 제공량이 정해져 있어서, 계정당 시간당 호출 수를 제한한다.
 * 엔진을 쓸 수 없으면(연결 실패, 시간 초과, 호출 한도, 엔진 오류) 503 OCR_UNAVAILABLE이다. 이때 이미지 내용은 로그에 남기지 않고 원인만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionOcrService {

    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final int CANDIDATE_LIMIT = 3;
    /** 자동으로 약을 정할 수 있는지 판단하려고 DB에서 가져오는 후보 수 (사용자에게 보여 주는 것은 CANDIDATE_LIMIT개) */
    private static final int MATCH_POOL_LIMIT = 10;
    private static final int MAX_LOOKUP_LENGTH = 50;
    private static final int RECOGNIZE_ATTEMPTS_PER_HOUR = 20;

    private final PatientAccessGuard patientAccessGuard;
    private final ConsentGuard consentGuard;
    private final PrescriptionOcrClient ocrClient;
    private final DrugService drugService;
    private final TransactionTemplate transactionTemplate;
    private final FixedWindowCounter recognizeLimiter = new FixedWindowCounter(20_000);

    /**
     * 접근 권한: 처방 등록으로 이어지는 흐름이라 처방 등록과 같은 쓰기 권한(CREATE)을 요구한다.
     * (본인 또는 읽기·쓰기 권한이 있는 보호자. 읽기 전용 보호자는 403, 연동 없는 사람은 404)
     *
     * 이 메서드 전체를 트랜잭션으로 묶지 않는다. 실제 OCR 엔진은 몇 초씩 걸릴 수 있어서, 그동안 DB 연결을
     * 붙잡고 있으면 동시 요청이 몰릴 때 연결이 바닥난다. DB가 필요한 접근 확인만 짧은 트랜잭션으로 한다.
     */
    public OcrRecognizeResponse recognize(Long userId, Long patientId, MultipartFile image) {
        // 가드가 먼저다: 권한 없는 사람에게는 이미지 검증 결과조차 알려주지 않는다
        transactionTemplate.executeWithoutResult(status ->
                patientAccessGuard.check(userId, patientId, AccessAction.CREATE, "prescription_ocr", null));
        // 처방전을 읽는 것도 약 등록 흐름이라 같은 동의(MEDICATION)가 필요하다
        consentGuard.requireMedication(userId);

        if (!recognizeLimiter.tryAcquire("ocr|" + userId, RECOGNIZE_ATTEMPTS_PER_HOUR, 3600, Instant.now().getEpochSecond())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        byte[] bytes = readAndValidate(image);
        OcrResult result;
        try {
            result = ocrClient.recognize(bytes);
        } catch (OcrUnavailableException e) {
            log.warn("처방전 인식을 쓸 수 없습니다: {}", e.getReason());
            throw new BusinessException(ErrorCode.OCR_UNAVAILABLE);
        }

        List<RecognizedMedication> recognized = result.items().stream().map(this::toResponse).toList();
        return new OcrRecognizeResponse(result.hospitalName(), result.pharmacyName(), result.prescribedDate(), recognized);
    }

    /** 비어 있지 않은지, 크기, 실제 형식(JPEG/PNG)을 확인하고 내용을 읽는다. 파일 이름은 쓰지도 기록하지도 않는다 */
    private byte[] readAndValidate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "이미지 파일이 비어 있습니다.");
        }
        // 스프링 설정(spring.servlet.multipart.max-file-size)이 먼저 막지만, 설정이 빠져도 여기서 한 번 더 막는다
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "이미지를 읽을 수 없습니다.");
        }
        // Content-Type 헤더와 확장자는 속일 수 있어서 파일 내용의 시그니처로 판별한다
        if (ImageSignature.detect(bytes) == null) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        return bytes;
    }

    /** OCR이 읽은 약 한 줄에 약물 DB 매칭 결과(자동 매칭 id, 후보)를 붙인다 */
    private RecognizedMedication toResponse(OcrResult.Item item) {
        List<DrugSuggestionResponse> pool = findPool(item.drugName());
        Long matchedId = DrugNameMatcher.matchId(item.drugName(),
                pool.stream().map(c -> new DrugNameMatcher.Candidate(c.drugId(), c.productName())).toList());
        return new RecognizedMedication(
                item.drugName(),
                matchedId,
                topCandidates(pool, matchedId),
                item.dosePerIntake(),
                item.timesPerDay(),
                item.totalDays(),
                item.prn(),
                item.mealRelation() == null ? null : item.mealRelation().name(),
                item.mealOffsetMin(),
                item.timingSlots());
    }

    /**
     * 읽은 이름으로 약물 DB를 검색해 후보를 찾는다. 검색어는 읽은 이름 그대로, 첫 숫자(함량) 앞까지, 괄호를 뺀 이름 순서로 시도하고
     * 결과가 나오면 멈춘다 - 함량이나 성분명 병기가 DB 표기와 달라도 같은 제품군이 후보로 나오게.
     * 자동완성과 같은 검색(부분 일치, 입력 이스케이프, 길이 제한)을 재사용한다.
     */
    private List<DrugSuggestionResponse> findPool(String rawName) {
        for (String term : DrugNameMatcher.searchTerms(rawName, MAX_LOOKUP_LENGTH)) {
            List<DrugSuggestionResponse> found = drugService.autocomplete(term, MATCH_POOL_LIMIT);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    /**
     * 사용자에게 보여 줄 후보 (최대 CANDIDATE_LIMIT개). 자동으로 정해진 제품이 있으면 맨 앞에 둔다.
     * 자동 선택은 "상품명이 같고 함량이 맞는 제품이 정확히 하나일 때만" 이뤄진다(DrugNameMatcher).
     * 같은 이름의 제품이 업체별로 여러 개면 어느 것인지 알 수 없어 자동으로 정하지 않는다 (잘못 정하면
     * 다른 약으로 병용금기 검사가 돌아가 위험하다).
     */
    private static List<DrugSuggestionResponse> topCandidates(List<DrugSuggestionResponse> pool, Long matchedId) {
        List<DrugSuggestionResponse> result = new ArrayList<>();
        if (matchedId != null) {
            pool.stream().filter(c -> matchedId.equals(c.drugId())).findFirst().ifPresent(result::add);
        }
        for (DrugSuggestionResponse c : pool) {
            if (result.size() >= CANDIDATE_LIMIT) {
                break;
            }
            if (!result.contains(c)) {
                result.add(c);
            }
        }
        return result;
    }

}