package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.domain.account.dto.PatientRegisterRequest;
import com.gamcho.yakyeon.domain.account.dto.PatientResponse;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.account.repository.AppUserRepository;
import com.gamcho.yakyeon.domain.account.repository.GuardianLinkRepository;
import com.gamcho.yakyeon.domain.account.repository.PatientRepository;
import com.gamcho.yakyeon.security.PhoneEncryptor;
import com.gamcho.yakyeon.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final PhoneEncryptor phoneEncryptor;
    private final TokenHasher tokenHasher; // SHA-256 해시 - Refresh Token과 동일 유틸 재사용

    /**
     * 복약자 등록.
     * - 호출 계정이 PATIENT: 본인을 복약자로 등록 (patient.user_id = 본인). 1인 1프로필만 허용.
     * - 호출 계정이 GUARDIAN: 앱 계정 없는 피보호자를 새로 등록 (patient.user_id = null),
     *   본인에게 ACTIVE guardian_link를 바로 만든다. 본인이 직접 입력한 정보라
     *   "타인 동의"가 필요한 위임 동의 플로우와는 다름(그건 기존 계정을 가진 복약자에게
     *   연동을 "요청"하는 경우에만 필요 - 다음 API에서 다룸).
     */
    @Transactional
    public PatientResponse registerPatient(Long userId, PatientRegisterRequest request) {
        AppUser user = appUserRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String normalizedPhone = normalizePhone(request.getPhone());
        String phoneHash = tokenHasher.sha256Hex(normalizedPhone);
        byte[] phoneEnc = phoneEncryptor.encrypt(normalizedPhone);

        if (user.getAccountType() == AppUser.AccountType.PATIENT) {
            return registerSelf(user, request, phoneEnc, phoneHash);
        }
        return registerDependent(user, request, phoneEnc, phoneHash);
    }

    private PatientResponse registerSelf(AppUser user, PatientRegisterRequest request,
                                         byte[] phoneEnc, String phoneHash) {
        if (patientRepository.findByUser_UserId(user.getUserId()).isPresent()) {
            throw new BusinessException(ErrorCode.PATIENT_ALREADY_REGISTERED);
        }

        Patient patient = Patient.builder()
                .user(user)
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .phoneEnc(phoneEnc)
                .phoneHash(phoneHash)
                .createdBy(user)
                .build();
        Patient saved = patientRepository.save(patient);

        return toResponse(saved, true);
    }

    private PatientResponse registerDependent(AppUser guardian, PatientRegisterRequest request,
                                              byte[] phoneEnc, String phoneHash) {
        Patient patient = Patient.builder()
                .user(null)
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .phoneEnc(phoneEnc)
                .phoneHash(phoneHash)
                .createdBy(guardian)
                .build();
        Patient saved = patientRepository.save(patient);

        GuardianLink link = GuardianLink.builder()
                .guardianUser(guardian)
                .patient(saved)
                // 앱 계정이 없는 복약자는 권한을 올려줄 사람이 없어 READ_WRITE (DB 트리거 default_proxy_link_scope와 같은 규칙)
                .permissionScope(GuardianLink.PermissionScope.READ_WRITE)
                .build();
        link.activate(GuardianLink.ConsentSubject.SELF);
        guardianLinkRepository.save(link);

        return toResponse(saved, false);
    }

    private PatientResponse toResponse(Patient patient, boolean linkedToSelf) {
        return new PatientResponse(patient.getPatientId(), patient.getName(), patient.getBirthDate(), linkedToSelf);
    }

    /** 하이픈·공백 등 숫자 아닌 문자를 전부 제거해서 정규화 (해시·암호화 전 동일 형식 보장) */
    private String normalizePhone(String raw) {
        return raw.replaceAll("[^0-9]", "");
    }
}