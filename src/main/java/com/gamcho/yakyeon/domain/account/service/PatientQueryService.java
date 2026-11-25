package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccess;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.account.dto.PatientDetailResponse;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientQueryService {

    private final PatientAccessGuard patientAccessGuard;

    /** 복약자 기본 정보 조회. 본인이거나 활성 연동이 있는 보호자만 볼 수 있다 (읽기 전용 권한으로 충분). */
    @Transactional(readOnly = true)
    public PatientDetailResponse getPatient(Long userId, Long patientId) {
        PatientAccess access = patientAccessGuard.check(userId, patientId, AccessAction.VIEW, "patient", patientId);
        Patient patient = access.patient();

        return new PatientDetailResponse(
                patient.getPatientId(),
                patient.getName(),
                patient.getBirthDate(),
                patient.getUser() != null,
                access.role().name(),
                access.scope() == null ? null : access.scope().name());
    }
}