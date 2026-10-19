package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.client.sms.SmsGateway;
import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.PhoneNumbers;
import com.gamcho.yakyeon.domain.account.dto.SignupPhoneConfirmResponse;
import com.gamcho.yakyeon.domain.account.dto.VerificationSendResponse;
import com.gamcho.yakyeon.domain.account.entity.PhoneVerification;
import com.gamcho.yakyeon.domain.account.repository.PhoneVerificationRepository;
import com.gamcho.yakyeon.security.PhoneHasher;
import com.gamcho.yakyeon.security.SignupProofSigner;
import com.gamcho.yakyeon.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 회원가입용 휴대폰 문자 인증 (purpose = SIGNUP, 아직 계정이 없어서 로그인 없이 호출한다).
 *
 * 흐름: send(번호) → confirm(인증 ID, 인증번호) → 증표 발급 → 가입 요청에 증표를 같이 보냄 → consume()으로 소진.
 * 보호자 연동용 인증(VerificationService)과는 목적이 달라서 서비스를 따로 둔다.
 *
 * TODO(배포 전): 번호별 한도(1시간 5건)만 걸려 있다. 서로 다른 번호로 대량 발송하는 것(요금·스팸)은
 *                IP 기반 호출 제한이 필요하다 (배포 전 체크리스트의 "공개 API 호출 제한").
 */
@Service
@RequiredArgsConstructor
public class SignupVerificationService {

    private static final long CODE_TTL_SECONDS = 300;      // 인증번호 유효시간 5분
    private static final long PROOF_TTL_SECONDS = 1800;    // 인증 완료 후 가입을 마쳐야 하는 시간 30분
    private static final int MAX_REQUESTS_PER_HOUR = 5;    // 같은 번호로 1시간에 발송 가능한 최대 건수

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final PhoneHasher phoneHasher;
    private final TokenHasher tokenHasher;
    private final SmsGateway smsGateway;
    private final SignupProofSigner signupProofSigner;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 인증 문자 발송. 이미 가입된 번호인지는 여기서 알려주지 않는다 - 인증번호를 받은 번호의 주인만
     * 가입 단계에서 중복 여부를 알 수 있게 해서, 남의 번호로 가입 여부를 캐묻는 걸 막는다.
     */
    @Transactional
    public VerificationSendResponse send(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (!PhoneNumbers.isValidMobile(phone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
        String phoneHash = phoneHasher.hash(phone);
        LocalDateTime now = LocalDateTime.now();

        // 같은 번호로 문자가 쏟아지는 걸 막는다 (스팸 / 발송 비용)
        long recent = phoneVerificationRepository.countByPhoneHashAndPurposeAndCreatedAtAfter(
                phoneHash, PhoneVerification.Purpose.SIGNUP, now.minusHours(1));
        if (recent >= MAX_REQUESTS_PER_HOUR) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMITED);
        }

        String code = generateCode();
        PhoneVerification verification = phoneVerificationRepository.save(PhoneVerification.builder()
                .phoneHash(phoneHash)
                .purpose(PhoneVerification.Purpose.SIGNUP)
                .codeHash(tokenHasher.sha256Hex(code))
                .expiresAt(now.plusSeconds(CODE_TTL_SECONDS))
                .linkId(null)
                .build());

        smsGateway.send(phone, "[약연] 인증번호는 [" + code + "]입니다. 5분 안에 입력해주세요.");

        return new VerificationSendResponse(verification.getVerificationId(), CODE_TTL_SECONDS);
    }

    /**
     * 인증번호 확인. 틀린 번호는 예외가 아니라 verified=false로 돌려준다 -
     * 예외를 던지면 트랜잭션이 롤백되면서 방금 올린 시도 횟수까지 같이 없어져서 10회 제한이 의미가 없어진다.
     */
    @Transactional
    public SignupPhoneConfirmResponse confirm(Long verificationId, String code) {
        // 행 락: 같은 인증에 동시 요청을 보내 시도 횟수 제한을 넘기는 걸 막는다
        PhoneVerification verification = phoneVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        // 가입용이 아닌 인증(보호자 연동·철회)은 존재 여부도 숨긴다
        if (verification.getPurpose() != PhoneVerification.Purpose.SIGNUP) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        // 이미 통과한 인증은 다시 확인해주지 않는다 - 확인해주면 인증이 끝난 뒤에도 인증번호를 계속 대입할 수 있다
        if (verification.isVerified()) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_COMPLETED);
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
        if (!match) {
            return new SignupPhoneConfirmResponse(false, null, null);
        }

        verification.markVerified();
        String token = signupProofSigner.issue(
                verification.getVerificationId(), verification.getPhoneHash(),
                Instant.now().plusSeconds(PROOF_TTL_SECONDS));
        return new SignupPhoneConfirmResponse(true, token, PROOF_TTL_SECONDS);
    }

    /**
     * 가입할 때 인증 증표를 확인하고 소진한다 (인증 행을 지운다 - 한 번 쓴 증표는 다시 쓸 수 없다).
     * 호출한 가입 트랜잭션에 합류하므로, 가입이 실패해서 롤백되면 이 삭제도 같이 취소돼 인증이 되살아난다.
     * 실패 이유(없음/다른 번호/만료/위조)는 구분하지 않고 같은 예외로 응답한다.
     */
    @Transactional
    public void consume(Long verificationId, String proofToken, String phoneHash) {
        PhoneVerification verification = phoneVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_COMPLETED));

        boolean valid = verification.getPurpose() == PhoneVerification.Purpose.SIGNUP
                && verification.isVerified()
                && verification.getPhoneHash().equals(phoneHash)
                && signupProofSigner.verify(proofToken, verificationId, phoneHash, Instant.now());
        if (!valid) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_COMPLETED);
        }
        phoneVerificationRepository.delete(verification);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}