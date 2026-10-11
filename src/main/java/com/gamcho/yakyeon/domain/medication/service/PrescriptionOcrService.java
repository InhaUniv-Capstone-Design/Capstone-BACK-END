package com.gamcho.yakyeon.domain.medication.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.drug.dto.DrugSuggestionResponse;
import com.gamcho.yakyeon.domain.drug.service.DrugService;
import com.gamcho.yakyeon.domain.medication.dto.OcrRecognizeResponse;
import com.gamcho.yakyeon.domain.medication.dto.RecognizedMedication;
import com.gamcho.yakyeon.domain.medication.ocr.ImageSignature;
import com.gamcho.yakyeon.domain.medication.ocr.OcrResult;
import com.gamcho.yakyeon.domain.medication.ocr.PrescriptionOcrClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 처방전 이미지 OCR (FR-ADD). 이미지를 읽어 약 목록 초안을 돌려줄 뿐 아무것도 저장하지 않는다 -
 * 사용자가 초안을 확인한 뒤 처방 등록 API를 따로 호출한다. 이미지도 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PrescriptionOcrService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int CANDIDATE_LIMIT = 3;
    private static final int MAX_LOOKUP_LENGTH = 50;

    private final PatientAccessGuard patientAccessGuard;
    private final PrescriptionOcrClient ocrClient;
    private final DrugService drugService;
    private final TransactionTemplate transactionTemplate;

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

        byte[] bytes = readAndValidate(image);
        OcrResult result = ocrClient.recognize(bytes);

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
        List<DrugSuggestionResponse> candidates = findCandidates(item.drugName());
        return new RecognizedMedication(
                item.drugName(),
                exactMatchId(item.drugName(), candidates),
                candidates,
                item.dosePerIntake(),
                item.timesPerDay(),
                item.totalDays(),
                item.prn(),
                item.mealRelation() == null ? null : item.mealRelation().name(),
                item.mealOffsetMin());
    }

    /**
     * 읽은 이름으로 약물 DB를 검색해 후보를 찾는다.
     * 결과가 없으면 숫자(함량) 앞까지만 잘라 다시 찾는다 - 함량을 잘못 읽은 경우에도 같은 제품군이 후보로 나오게.
     * 자동완성과 같은 검색(부분 일치, 입력 이스케이프, 길이 제한)을 재사용한다.
     */
    private List<DrugSuggestionResponse> findCandidates(String rawName) {
        String name = squeeze(rawName);
        if (name.length() > MAX_LOOKUP_LENGTH) {
            name = name.substring(0, MAX_LOOKUP_LENGTH);   // OCR이 이상하게 길게 읽어도 검색 제한(50자)에 걸려 실패하지 않게
        }
        List<DrugSuggestionResponse> found = drugService.autocomplete(name, CANDIDATE_LIMIT);
        if (found.isEmpty()) {
            String withoutStrength = name.replaceAll("[0-9].*$", "").trim();
            if (!withoutStrength.equals(name)) {
                found = drugService.autocomplete(withoutStrength, CANDIDATE_LIMIT);
            }
        }
        return found;
    }

    /**
     * 후보 중 읽은 이름과 정확히 일치하는 제품이 "하나뿐일 때만" 그 id를 돌려준다.
     * 같은 이름의 제품이 업체별로 여러 개면 어느 것인지 알 수 없어 자동으로 정하지 않는다 (잘못 정하면
     * 다른 약으로 병용금기 검사가 돌아가 위험하다).
     */
    private Long exactMatchId(String rawName, List<DrugSuggestionResponse> candidates) {
        String target = normalize(rawName);
        List<DrugSuggestionResponse> exact = candidates.stream()
                .filter(c -> normalize(c.productName()).equals(target))
                .toList();
        return exact.size() == 1 ? exact.get(0).drugId() : null;
    }

    /** 앞뒤 공백 제거, 연속 공백을 하나로 */
    private static String squeeze(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /** 비교용: 공백을 모두 없애고 소문자로 */
    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}