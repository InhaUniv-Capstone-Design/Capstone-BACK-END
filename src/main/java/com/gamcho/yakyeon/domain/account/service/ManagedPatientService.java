package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.domain.account.consent.SharedItemRules;
import com.gamcho.yakyeon.domain.account.dto.ManagedPatientResponse;
import com.gamcho.yakyeon.domain.account.logic.PhoneMasker;
import com.gamcho.yakyeon.domain.account.repository.AccountQueryRepository;
import com.gamcho.yakyeon.security.PhoneEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 내가 볼 수 있는 복약자 목록 (복약자 선택·전환 화면). 본인(앱 계정이 있는 복약자)이면 본인이 맨 앞에,
 * 그 뒤에 내가 ACTIVE 연동으로 보호하는 복약자들이 연동 시각순으로 나온다. 연동이 해제된 복약자는 즉시 빠진다.
 * 내 정보만 조회하므로 복약자 접근 가드는 거치지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ManagedPatientService {

    private final AccountQueryRepository accountQueryRepository;
    private final PhoneEncryptor phoneEncryptor;

    @Transactional(readOnly = true)
    public List<ManagedPatientResponse> list(Long userId) {
        return accountQueryRepository.findManagedPatients(userId).stream().map(this::toResponse).toList();
    }

    /**
     * 보호자로 연동된 행에는 복약자가 허용한 열람 항목(shared_items)을 담는다. 복약자의 휴대폰 번호는 이름·연락처(PERSONAL_INFO)를
     * 허용했을 때만, 그리고 가려서(010-****-5678) 내려준다. 본인 행에는 해당 없다.
     */
    private ManagedPatientResponse toResponse(AccountQueryRepository.ManagedRow r) {
        boolean guardianRow = "GUARDIAN".equals(r.myRole());
        List<String> items = guardianRow ? SharedItemRules.parse(r.sharedItems()) : null;
        String phoneMasked = null;
        if (guardianRow && items.contains(SharedItemRules.PERSONAL_INFO) && r.phoneEnc() != null) {
            phoneMasked = PhoneMasker.mask(phoneEncryptor.decrypt(r.phoneEnc()));
        }
        return new ManagedPatientResponse(r.patientId(), r.name(), r.birthDate(), r.hasAccount(), r.myRole(), r.linkId(),
                r.permissionScope(), r.linkedAt(), guardianRow ? r.relationship() : null, items, phoneMasked);
    }
}