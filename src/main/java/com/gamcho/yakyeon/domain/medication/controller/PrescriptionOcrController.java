package com.gamcho.yakyeon.domain.medication.controller;

import com.gamcho.yakyeon.domain.medication.dto.OcrRecognizeResponse;
import com.gamcho.yakyeon.domain.medication.service.PrescriptionOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 처방전 이미지 OCR (Mock). 로그인 필요.
 * 명세(POST /prescriptions/ocr)에는 복약자 번호가 없지만, 쓰기 권한 확인을 위해 누구의 처방전인지 알아야 해서
 * patient_id를 필수 쿼리 파라미터로 받는다. 아무것도 저장하지 않는 확인용 초안만 돌려준다.
 */
@RestController
@RequiredArgsConstructor
public class PrescriptionOcrController {

    private final PrescriptionOcrService prescriptionOcrService;

    @PostMapping(value = "/prescriptions/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrRecognizeResponse> recognize(
            @AuthenticationPrincipal Long userId,
            @RequestParam("patient_id") Long patientId,
            @RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(prescriptionOcrService.recognize(userId, patientId, image));
    }
}