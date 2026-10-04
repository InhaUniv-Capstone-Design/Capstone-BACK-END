package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.dto.ConsentHistoryResponse;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.ConsentLog;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.account.repository.ConsentLogRepository;
import com.gamcho.yakyeon.domain.account.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsentHistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final ConsentLogRepository consentLogRepository;

    /**
     * 본인의 동의 이력 (최신순).
     *  - 복약자 계정: 자기 복약자 프로필에 대한 모든 이력. 상대 보호자는 로그인 아이디로 보여준다.
     *  - 보호자 계정: 자기가 당사자인 이력. 복약자 이름은 마스킹해서 보여준다
     *    (철회된 뒤에도 이력은 볼 수 있어야 하지만, 접근이 끊긴 사람에게 실명이 계속 보이면 안 된다).
     * 범위가 잘못 넘어오면 에러 대신 보정한다 (page는 0 이상, size는 1~100).
     */
    @Transactional(readOnly = true)
    public List<ConsentHistoryResponse> getMyHistory(Long userId, int page, int size) {
        AppUser user = appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("consentLogId")));

        if (user.getAccountType() == AppUser.AccountType.PATIENT) {
            return patientHistory(userId, pageable);
        }
        return guardianHistory(userId, pageable);
    }

    private List<ConsentHistoryResponse> patientHistory(Long userId, Pageable pageable) {
        Optional<Patient> patient = patientRepository.findByUser_UserId(userId);
        if (patient.isEmpty()) {
            return List.of(); // 아직 복약자 프로필을 등록하지 않은 계정
        }

        List<ConsentLog> logs = consentLogRepository.findByPatientId(patient.get().getPatientId(), pageable);

        List<Long> guardianIds = logs.stream().map(ConsentLog::getGuardianUserId).distinct().toList();
        Map<Long, String> loginIds = appUserRepository.findAllById(guardianIds).stream()
                .collect(Collectors.toMap(AppUser::getUserId, AppUser::getLoginId));

        return logs.stream()
                .map(l -> toResponse(l, loginIds.get(l.getGuardianUserId()), null))
                .toList();
    }

    private List<ConsentHistoryResponse> guardianHistory(Long userId, Pageable pageable) {
        List<ConsentLog> logs = consentLogRepository.findByGuardianUserId(userId, pageable);

        List<Long> patientIds = logs.stream().map(ConsentLog::getPatientId).distinct().toList();
        Map<Long, String> names = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getPatientId, p -> GuardianLinkService.maskName(p.getName())));

        return logs.stream()
                .map(l -> toResponse(l, null, names.get(l.getPatientId())))
                .toList();
    }

    private ConsentHistoryResponse toResponse(ConsentLog l, String guardianLoginId, String patientName) {
        return new ConsentHistoryResponse(
                l.getConsentLogId(),
                l.getLinkId(),
                l.getAction().name(),
                l.getConsentSubject().name(),
                l.getPermissionScope().name(),
                l.getLegalRepName(),
                guardianLoginId,
                patientName,
                ApiTime.toInstant(l.getOccurredAt()));
    }
}