package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.domain.account.dto.ManagedPatientResponse;
import com.gamcho.yakyeon.domain.account.repository.AccountQueryRepository;
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

    @Transactional(readOnly = true)
    public List<ManagedPatientResponse> list(Long userId) {
        return accountQueryRepository.findManagedPatients(userId);
    }
}