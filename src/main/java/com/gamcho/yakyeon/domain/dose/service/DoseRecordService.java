package com.gamcho.yakyeon.domain.dose.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.dose.dto.DoseResponse;
import com.gamcho.yakyeon.domain.dose.logic.DoseRecordRules;
import com.gamcho.yakyeon.domain.dose.repository.DoseRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 앱에서 복약 기록·정정 ("먹었어요 + 몇 시에" / "안 먹었어요"). 복약자의 건강 기록을 바꾸므로 수정 권한이 필요하다
 * (복약자 본인 또는 읽기·쓰기 권한 보호자). 연동이 없는 사람에게는 "없는 예정"과 같은 오류를 줘서
 * 번호가 존재한다는 사실이 새지 않게 한다.
 *
 * 규칙:
 *  - 정상/지연은 복용 시각으로 서버가 정한다 (정상 인정 시각까지면 정상, 그 뒤는 지연).
 *  - 당일(한국 날짜)의 예정만 기록·정정할 수 있다 (DB 정정 함수와 같은 기준).
 *  - 판정 전 예정의 첫 기록은 앱 판정(MANUAL)이고, 이미 판정된 예정의 변경은 정정으로 이력이 남는다.
 *  - 같은 내용을 다시 보내면(버튼을 두 번 누른 경우) 이력을 또 남기지 않고 그대로 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class DoseRecordService {

    private static final String RESOURCE_TYPE = "dose";

    private final PatientAccessGuard patientAccessGuard;
    private final DoseRecordRepository doseRecordRepository;
    private final DoseQueryService doseQueryService;

    /** "먹었어요". requestedTakenAt이 없으면 지금 먹은 것으로 기록한다 */
    @Transactional
    public DoseResponse taken(Long userId, Long doseId, OffsetDateTime requestedTakenAt) {
        DoseRecordRepository.DoseState dose = authorizeAndLock(userId, doseId);
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);
        OffsetDateTime takenAt = requestedTakenAt == null ? now : requestedTakenAt;

        DoseRecordRules.takenAtViolation(takenAt, dose.scheduledAt(), now).ifPresent(message -> {
            throw new BusinessException(ErrorCode.INVALID_TAKEN_AT, message);
        });
        if (!DoseRecordRules.sameDay(dose.scheduledAt(), now, ApiTime.SERVICE_ZONE)) {
            throw new BusinessException(ErrorCode.DOSE_EXPIRED);
        }

        String status = DoseRecordRules.statusFor(takenAt, dose.normalUntil());
        if ("PENDING".equals(dose.status())) {
            if (doseRecordRepository.recordFirst(doseId, status, now, userId, takenAt) == 0) {
                throw new BusinessException(ErrorCode.DOSE_RECORD_FAILED);
            }
        } else {
            if (DoseRecordRules.isSameRecord(dose.status(), dose.takenAt(), status, takenAt)) {
                return doseQueryService.find(doseId);
            }
            correct(doseId, userId, status, takenAt, now);
        }
        return doseQueryService.find(doseId);
    }

    /**
     * "안 먹었어요". 마감 전이면 다시 예정으로 돌아가서 나중에 먹으면 기록할 수 있고, 마감이 지났으면 누락이 된다.
     * 아직 기록이 없는 예정에는 되돌릴 게 없어서 거절한다.
     */
    @Transactional
    public DoseResponse notTaken(Long userId, Long doseId) {
        DoseRecordRepository.DoseState dose = authorizeAndLock(userId, doseId);
        OffsetDateTime now = OffsetDateTime.now(ApiTime.SERVICE_ZONE);

        if ("PENDING".equals(dose.status())) {
            throw new BusinessException(ErrorCode.DOSE_NOT_DECIDED);
        }
        if (!DoseRecordRules.sameDay(dose.scheduledAt(), now, ApiTime.SERVICE_ZONE)) {
            throw new BusinessException(ErrorCode.DOSE_EXPIRED);
        }
        // 이미 누락이고 마감도 지났으면 바뀔 게 없다
        if ("MISSED".equals(dose.status())
                && "MISSED".equals(DoseRecordRules.notTakenResult(dose.closeAt(), now))) {
            return doseQueryService.find(doseId);
        }
        correct(doseId, userId, "NOT_TAKEN", null, now);
        return doseQueryService.find(doseId);
    }

    /**
     * 권한을 먼저 확인하고(행을 잠그기 전에) 그다음 예정을 잠근다. 권한 없는 사람이 남의 예정을 잠글 수 없다.
     */
    private DoseRecordRepository.DoseState authorizeAndLock(Long userId, Long doseId) {
        Long patientId = doseRecordRepository.findPatientId(doseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOSE_NOT_FOUND));
        try {
            patientAccessGuard.check(userId, patientId, AccessAction.UPDATE, RESOURCE_TYPE, doseId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PATIENT_NOT_FOUND) {
                throw new BusinessException(ErrorCode.DOSE_NOT_FOUND);
            }
            throw e;
        }
        return doseRecordRepository.lock(doseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOSE_NOT_FOUND));
    }

    /**
     * DB 정정 함수 호출. 미리 확인한 규칙을 DB가 한 번 더 확인하는데, 그래도 DB가 거절하면
     * DB 오류 문구를 그대로 내보내지 않고 일반 오류로 바꾼다 (이 호출이 실패하면 트랜잭션 전체가 취소된다).
     */
    private void correct(Long doseId, Long userId, String result, OffsetDateTime takenAt, OffsetDateTime now) {
        try {
            doseRecordRepository.correct(doseId, userId, result, takenAt, now);
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DOSE_RECORD_FAILED);
        }
    }
}