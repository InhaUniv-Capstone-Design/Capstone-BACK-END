package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.client.sms.SmsGateway;
import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.PhoneNumbers;
import com.gamcho.yakyeon.domain.account.dto.VerificationSendRequest;
import com.gamcho.yakyeon.domain.account.dto.VerificationSendResponse;
import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import com.gamcho.yakyeon.domain.account.entity.Patient;
import com.gamcho.yakyeon.domain.account.entity.PhoneVerification;
import com.gamcho.yakyeon.domain.account.repository.GuardianLinkRepository;
import com.gamcho.yakyeon.domain.account.repository.PhoneVerificationRepository;
import com.gamcho.yakyeon.security.PhoneEncryptor;
import com.gamcho.yakyeon.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final long CODE_TTL_SECONDS = 300;      // 인증번호 유효시간 5분
    private static final int MAX_REQUESTS_PER_HOUR = 5;    // 같은 번호로 1시간에 발송 가능한 최대 건수

    private final GuardianAccessChecker guardianAccessChecker;
    private final GuardianLinkRepository guardianLinkRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final PhoneEncryptor phoneEncryptor;
    private final TokenHasher tokenHasher;
    private final SmsGateway smsGateway;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 인증 문자 발송.
     * - 성인 복약자: 복약자 본인 번호(DB에 암호화돼 있던 값을 복호화)로 발송, purpose=DELEGATION
     * - 만 14세 미만: 요청에 담긴 법정대리인 번호로 발송, purpose=LEGAL_REP
     * 인증 기록은 이 연동 요청(link_id)에 묶어서 저장한다.
     */
    @Transactional
    public VerificationSendResponse send(Long userId, Long linkId, VerificationSendRequest request) {
        guardianAccessChecker.requireGuardian(userId);

        GuardianLink link = guardianLinkRepository.findByLinkIdAndGuardianUser_UserId(linkId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_FOUND));
        if (link.getStatus() != GuardianLink.LinkStatus.PENDING) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_PENDING);
        }

        Patient patient = link.getPatient();
        PhoneVerification.Purpose purpose;
        String targetPhone;

        if (patient.isUnder14()) {
            String legalRepPhone = PhoneNumbers.normalize(request == null ? null : request.getLegalRepPhone());
            if (legalRepPhone.isEmpty()) {
                throw new BusinessException(ErrorCode.LEGAL_REP_PHONE_REQUIRED);
            }
            if (!PhoneNumbers.isValidMobile(legalRepPhone)) {
                throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
            }
            // 미성년 본인 휴대폰으로 인증받아서 "법정대리인 동의"로 처리되는 걸 막는다
            if (tokenHasher.sha256Hex(legalRepPhone).equals(patient.getPhoneHash())) {
                throw new BusinessException(ErrorCode.LEGAL_REP_PHONE_SAME_AS_PATIENT);
            }
            purpose = PhoneVerification.Purpose.LEGAL_REP;
            targetPhone = legalRepPhone;
        } else {
            purpose = PhoneVerification.Purpose.DELEGATION;
            targetPhone = phoneEncryptor.decrypt(patient.getPhoneEnc());
        }

        String targetHash = tokenHasher.sha256Hex(targetPhone);
        LocalDateTime now = LocalDateTime.now();

        // 같은 번호로 문자가 쏟아지는 걸 막는다 (스팸 / 발송 비용).
        // 목적별로 따로 센다 - 문자 인증 철회(REVOKE)는 공개 API라 남이 반복 호출할 수 있는데,
        // 목적을 안 나누면 그 호출만으로 이 번호의 위임 동의 문자까지 한도에 걸려 막힌다.
        long recent = phoneVerificationRepository.countByPhoneHashAndPurposeAndCreatedAtAfter(
                targetHash, purpose, now.minusHours(1));
        if (recent >= MAX_REQUESTS_PER_HOUR) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMITED);
        }

        String code = generateCode();
        PhoneVerification verification = phoneVerificationRepository.save(PhoneVerification.builder()
                .phoneHash(targetHash)
                .purpose(purpose)
                .codeHash(tokenHasher.sha256Hex(code))
                .expiresAt(now.plusSeconds(CODE_TTL_SECONDS))
                .linkId(link.getLinkId())
                .build());

        smsGateway.send(targetPhone, "[약연] 인증번호는 [" + code + "]입니다. 5분 안에 입력해주세요.");

        return new VerificationSendResponse(verification.getVerificationId(), CODE_TTL_SECONDS);
    }

    /**
     * 인증번호 확인. 틀린 번호는 예외가 아니라 verified=false로 돌려준다 -
     * 예외를 던지면 트랜잭션이 롤백되면서 방금 올린 시도 횟수까지 같이 없어져서
     * 10회 제한이 의미가 없어지기 때문이다.
     */
    @Transactional
    public boolean confirm(Long userId, Long verificationId, String code) {
        guardianAccessChecker.requireGuardian(userId);

        // 행 락: 같은 인증에 동시 요청을 보내 시도 횟수 제한을 넘기는 걸 막는다
        PhoneVerification verification = phoneVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        // 이 인증이 속한 연동 요청의 보호자 본인만 확인할 수 있다 (남의 것이면 존재 여부도 숨김)
        if (verification.getLinkId() == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        GuardianLink link = guardianLinkRepository.findByLinkIdAndGuardianUser_UserId(verification.getLinkId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));
        if (link.getStatus() != GuardianLink.LinkStatus.PENDING) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_NOT_PENDING);
        }

        if (verification.isVerified()) {
            return true;
        }
        if (verification.isExpired()) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        if (!verification.canAttempt()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        verification.increaseAttempt();

        // 해시 문자열 비교도 일정 시간에 끝나는 방식으로 (비교 시간 차이로 값을 추측하는 공격 방지)
        boolean match = MessageDigest.isEqual(
                tokenHasher.sha256Hex(code).getBytes(StandardCharsets.UTF_8),
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (match) {
            verification.markVerified();
        }
        return match;
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}