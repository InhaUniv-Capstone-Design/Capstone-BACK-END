package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.client.sms.SmsGateway;
import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.PhoneNumbers;
import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.account.entity.PhoneVerification;
import com.gamcho.yakyeon.domain.account.repository.GuardianLinkRepository;
import com.gamcho.yakyeon.domain.account.repository.PatientRepository;
import com.gamcho.yakyeon.domain.account.repository.PhoneVerificationRepository;
import com.gamcho.yakyeon.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 문자 인증 기반 동의 철회 (FR-AUTH-014).
 * 앱 계정이 없는 복약자는 로그인할 수 없으므로, 본인 휴대폰 번호로 받은 인증번호로
 * 직접 철회할 수 있게 하는 공개(로그인 불필요) 흐름이다.
 *
 * 공개 API라서 두 가지를 지킨다:
 *  1) 응답으로 "이 번호가 복약자 번호인지"가 드러나지 않게 한다 - 번호가 없든, 연동이 없든,
 *     코드가 틀리든, 한도를 넘었든 요청에 대한 응답은 모두 같은 모양이다.
 *  2) 문자는 ACTIVE 연동이 있는 복약자 번호로만 보낸다 - 아무 번호로나 문자를 보내는 통로가 되지 않게.
 *
 * 알려진 한계: IP 단위 호출 제한이 없다. 한 번호당 시간당 5건 + 인증당 10회 시도 제한만 있으므로
 * 공개 배포 전에 IP 기반 제한(rate limit)을 추가해야 한다.
 */
@Service
@RequiredArgsConstructor
public class RevocationService {

    private static final long CODE_TTL_SECONDS = 300;
    private static final int MAX_REQUESTS_PER_HOUR = 5;

    private final PatientRepository patientRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final GuardianLinkService guardianLinkService;
    private final TokenHasher tokenHasher;
    private final SmsGateway smsGateway;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 1단계: 인증번호 발송 요청. 항상 같은 응답을 돌려준다.
     * 번호 형식이 틀린 경우만 400 (이건 번호의 회원 여부와 무관한 입력 검증이라 구분돼도 무방).
     */
    @Transactional
    public long requestCode(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (!PhoneNumbers.isValidMobile(phone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
        String hash = tokenHasher.sha256Hex(phone);

        boolean hasActiveLink = !findActiveLinks(hash).isEmpty();
        if (hasActiveLink) {
            // 한도를 넘으면 에러로 알리지 않고 조용히 발송만 건너뛴다.
            // 에러를 던지면 "한도 에러가 나는 번호 = 회원 번호"라는 사실이 드러난다.
            long recent = phoneVerificationRepository.countByPhoneHashAndPurposeAndCreatedAtAfter(
                    hash, PhoneVerification.Purpose.REVOKE, LocalDateTime.now().minusHours(1));
            if (recent < MAX_REQUESTS_PER_HOUR) {
                String code = String.format("%06d", secureRandom.nextInt(1_000_000));
                phoneVerificationRepository.save(PhoneVerification.builder()
                        .phoneHash(hash)
                        .purpose(PhoneVerification.Purpose.REVOKE)
                        .codeHash(tokenHasher.sha256Hex(code))
                        .expiresAt(LocalDateTime.now().plusSeconds(CODE_TTL_SECONDS))
                        .linkId(null) // 철회 인증은 특정 연동이 아니라 "이 번호의 복약자 전체"에 대한 것
                        .build());
                smsGateway.send(phone, "[약연] 보호자 연동 해제 인증번호는 [" + code + "]입니다. 5분 안에 입력해주세요.");
            }
        }
        return CODE_TTL_SECONDS;
    }

    /**
     * 2단계: 인증번호 확인과 동시에 철회 실행. 이 번호를 가진 복약자의 ACTIVE 연동을 모두 끊고
     * 끊은 개수를 돌려준다. 실패는 사유를 구분하지 않고 0을 돌려준다.
     * (틀린 코드로 예외를 던지면 트랜잭션이 롤백돼 시도 횟수가 안 남으므로 예외 대신 0을 반환)
     */
    @Transactional
    public int confirm(String rawPhone, String code) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (!PhoneNumbers.isValidMobile(phone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
        String hash = tokenHasher.sha256Hex(phone);

        // 락을 걸고 조회 - 동시 요청으로 시도 횟수 제한을 넘기는 걸 막는다
        Optional<PhoneVerification> found = phoneVerificationRepository
                .findFirstByPhoneHashAndPurposeOrderByCreatedAtDesc(hash, PhoneVerification.Purpose.REVOKE);
        if (found.isEmpty()) {
            return 0;
        }
        PhoneVerification verification = found.get();
        // 만료·이미 사용됨·시도 초과를 구분하지 않고 실패로 처리
        if (!verification.canAttempt()) {
            return 0;
        }

        verification.increaseAttempt();
        boolean match = MessageDigest.isEqual(
                tokenHasher.sha256Hex(code).getBytes(StandardCharsets.UTF_8),
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!match) {
            return 0;
        }
        verification.markVerified(); // 한 번 쓴 인증은 다시 쓸 수 없다 (canAttempt가 false가 됨)

        List<GuardianLink> links = findActiveLinks(hash);
        for (GuardianLink link : links) {
            guardianLinkService.revokeLink(link, verification.getVerificationId());
        }
        return links.size();
    }

    /** 이 번호(해시)를 가진 모든 복약자의 ACTIVE 연동. 같은 번호의 복약자가 여러 행일 수 있다. */
    private List<GuardianLink> findActiveLinks(String phoneHash) {
        List<Long> patientIds = patientRepository.findAllByPhoneHash(phoneHash).stream()
                .map(Patient::getPatientId)
                .toList();
        if (patientIds.isEmpty()) {
            return List.of();
        }
        return guardianLinkRepository.findByPatient_PatientIdInAndStatus(
                patientIds, GuardianLink.LinkStatus.ACTIVE);
    }
}