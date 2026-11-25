package com.gamcho.yakyeon.domain.share.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.access.AccessAction;
import com.gamcho.yakyeon.domain.account.access.PatientAccessGuard;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.account.repository.PatientRepository;
import com.gamcho.yakyeon.domain.interaction.repository.DurTabooLookup;
import com.gamcho.yakyeon.domain.medication.repository.PatientMedicationRepository;
import com.gamcho.yakyeon.domain.share.dto.ShareSummaryResponse;
import com.gamcho.yakyeon.domain.share.logic.ShareSummaryRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 복약 이력 공유 요약 (의료진에게 보여 줄 화면의 데이터). 복약자의 이름·생년월일과 복약 정보가 한꺼번에 나가므로
 * 접근 가드를 거치고, 접근 기록에는 "내보내기(EXPORT)"로 남는다(본인이 만들어도 남는다).
 *
 * 서버는 공유한 내용을 저장하지 않는다. 이미지(PNG)는 앱이 이 요약으로 만든다.
 * 병용금기는 저장된 검사 결과가 아니라 지금의 약 기록과 DUR 데이터로 다시 계산한다. 저장된 검사는 "지금 복용 중인 약"만
 * 대상으로 해서, 공유 기간 안에 끝난 약이 끼어 있던 쌍을 알 수 없기 때문이다. 아무것도 저장하지 않는다.
 * 이 서비스는 읽기만 하고, 규칙을 적용해 응답을 만드는 일은 ShareSummaryAssembler가 한다.
 */
@Service
@RequiredArgsConstructor
public class ShareSummaryService {

    private static final String RESOURCE_TYPE = "share_summary";

    private final PatientAccessGuard patientAccessGuard;
    private final PatientRepository patientRepository;
    private final PatientMedicationRepository patientMedicationRepository;
    private final DurTabooLookup durTabooLookup;

    @Transactional(readOnly = true)
    public ShareSummaryResponse get(Long userId, Long patientId, LocalDate from, LocalDate to) {
        patientAccessGuard.check(userId, patientId, AccessAction.EXPORT, RESOURCE_TYPE, null);

        ShareSummaryRules.Period period;
        try {
            period = ShareSummaryRules.resolvePeriod(from, to, ApiTime.today());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_QUERY, e.getMessage());
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATIENT_NOT_FOUND));

        // 삭제되지 않은 약 전부. 공유 기간과 상관있는 약만 고르는 일은 조립 단계가 한다
        List<ShareSummaryAssembler.MedicationInput> medications = patientMedicationRepository
                .findAliveByPatientId(patientId).stream()
                .map(m -> new ShareSummaryAssembler.MedicationInput(
                        m.getMedicationId(), m.getInputName(),
                        m.getDrug() == null ? null : m.getDrug().getItemSeq(),
                        m.getStartDate(), m.getEndDate(), m.getEndedOn(),
                        m.getPrescription() == null ? null : m.getPrescription().getHospitalName()))
                .toList();

        Optional<LocalDate> datasetBaseDate = durTabooLookup.latestSuccessfulDataset()
                .map(DurTabooLookup.DatasetInfo::baseDate);

        return ShareSummaryAssembler.assemble(patient.getName(), patient.getBirthDate(), period, medications,
                datasetBaseDate, durTabooLookup::findPairs);
    }
}