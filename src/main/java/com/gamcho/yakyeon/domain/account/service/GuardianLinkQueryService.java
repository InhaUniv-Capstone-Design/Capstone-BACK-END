package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.ApiTime;
import com.gamcho.yakyeon.domain.account.dto.GuardianLinkItemResponse;
import com.gamcho.yakyeon.domain.account.repository.AccountQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 복약자 본인이 자기에게 연동된 보호자 목록을 본다 (설정 > 보호자 연동). 여기서 받은 link_id로
 * 연동 해제(DELETE /guardian-links/{id})와 권한 변경(PATCH .../permission)을 호출한다.
 * 복약자 본인만 볼 수 있다: 보호자(읽기·쓰기 권한이 있어도)나 남에게는 복약자가 있는지도 알려주지 않으려고
 * "없는 복약자"와 같은 오류를 준다.
 */
@Service
@RequiredArgsConstructor
public class GuardianLinkQueryService {

    private static final Set<String> STATUSES = Set.of("PENDING", "ACTIVE", "REJECTED", "REVOKED");
    private static final List<String> DEFAULT_STATUSES = List.of("ACTIVE", "PENDING");

    private final AccountQueryRepository accountQueryRepository;

    @Transactional(readOnly = true)
    public List<GuardianLinkItemResponse> list(Long userId, Long patientId, String status) {
        List<String> statuses = DEFAULT_STATUSES;
        if (status != null) {
            if (!STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.INVALID_QUERY, "status는 PENDING, ACTIVE, REJECTED, REVOKED 중 하나여야 합니다.");
            }
            statuses = List.of(status);
        }
        if (!accountQueryRepository.isOwnActivePatient(userId, patientId)) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_FOUND);
        }
        return accountQueryRepository.findGuardianLinks(patientId, statuses).stream()
                .map(r -> new GuardianLinkItemResponse(r.linkId(),
                        r.guardianName() == null ? null : GuardianLinkService.maskName(r.guardianName()),
                        r.guardianLoginId(), r.status(), r.permissionScope(), r.consentSubject(),
                        r.consentedAt() == null ? null : r.consentedAt().atZoneSameInstant(ApiTime.SERVICE_ZONE).toOffsetDateTime()))
                .toList();
    }
}