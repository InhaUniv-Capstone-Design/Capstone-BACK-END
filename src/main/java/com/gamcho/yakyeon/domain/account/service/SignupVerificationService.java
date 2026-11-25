package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.client.octomo.OctomoClient;
import com.gamcho.yakyeon.client.octomo.OctomoUnavailableException;
import com.gamcho.yakyeon.client.sms.SmsGateway;
import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.PhoneNumbers;
import com.gamcho.yakyeon.domain.account.dto.SignupPhoneConfirmResponse;
import com.gamcho.yakyeon.domain.account.dto.SignupPhoneSendResponse;
import com.gamcho.yakyeon.domain.account.entity.PhoneVerification;
import com.gamcho.yakyeon.domain.account.logic.OctomoCheckThrottle;
import com.gamcho.yakyeon.domain.account.logic.VerificationWindow;
import com.gamcho.yakyeon.domain.account.repository.PhoneVerificationRepository;
import com.gamcho.yakyeon.security.PhoneHasher;
import com.gamcho.yakyeon.security.SignupProofSigner;
import com.gamcho.yakyeon.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 * 두 가지 방식이 있다 (phone-verification.provider):
 *  - mock(기본, 개발용): 인증번호를 서버 로그에 남기고, 사용자가 그 번호를 입력한다.
 *  - octomo: 서버는 인증번호를 문자로 보내지 않는다. 앱이 받은 인증번호(message_text)를 사용자가 자기 휴대폰에서
 *    OCTOMO 대표번호로 문자로 보내면, 확인할 때 서버가 OCTOMO에 "이 번호에서 이 번호가 왔는가"를 조회한다.
 *
 * 남용 방어: 번호별 한도(1시간 5건), 접속 주소별 호출 제한(컨트롤러의 @RateLimit), 인증 하나당 시도 10회,
 * OCTOMO 하루 호출 한도와 동시 호출 제한(OctomoHttpClient).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupVerificationService {

    private static final long CODE_TTL_SECONDS = 300;      // 인증번호 유효시간 5분
    private static final long PROOF_TTL_SECONDS = 1800;    // 인증 완료 후 가입을 마쳐야 하는 시간 30분
    private static final int MAX_REQUESTS_PER_HOUR = 5;    // 같은 번호로 1시간에 발송 가능한 최대 건수
    private static final long MIN_OCTOMO_CHECK_INTERVAL_MILLIS = 1500;   // 같은 인증에 OCTOMO를 다시 묻기까지 최소 간격 (앱은 2초 간격으로 확인한다)

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final PhoneHasher phoneHasher;
    private final TokenHasher tokenHasher;
    private final SmsGateway smsGateway;
    private final ObjectProvider<OctomoClient> octomoProvider;
    private final SignupProofSigner signupProofSigner;

    private final SecureRandom secureRandom = new SecureRandom();
    private final OctomoCheckThrottle octomoCheckThrottle =
            new OctomoCheckThrottle(MIN_OCTOMO_CHECK_INTERVAL_MILLIS, CODE_TTL_SECONDS * 1000);

    /**
     * 인증 문자 발송. 이미 가입된 번호인지는 여기서 알려주지 않는다 - 인증번호를 받은 번호의 주인만
     * 가입 단계에서 중복 여부를 알 수 있게 해서, 남의 번호로 가입 여부를 캐묻는 걸 막는다.
     */
    @Transactional
    public SignupPhoneSendResponse send(String rawPhone) {
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

        OctomoClient octomo = octomoProvider.getIfAvailable();
        if (octomo == null) {
            // 개발용: 문자를 보낸 것처럼 서버 로그에 남기고, 사용자가 그 번호를 입력한다
            smsGateway.send(phone, "[약연] 인증번호는 [" + code + "]입니다. 5분 안에 입력해주세요.");
            return new SignupPhoneSendResponse(verification.getVerificationId(), CODE_TTL_SECONDS, null, null);
        }
        // OCTOMO: 인증번호를 앱에 주고, 사용자가 자기 휴대폰에서 대표번호로 문자를 보낸다. 번호의 주인이 아니면 보낼 수 없다.
        return new SignupPhoneSendResponse(verification.getVerificationId(), CODE_TTL_SECONDS,
                code, octomo.representativeNumber());
    }

    /**
     * 인증번호 확인. 틀린 번호는 예외가 아니라 verified=false로 돌려준다 -
     * 예외를 던지면 트랜잭션이 롤백되면서 방금 올린 시도 횟수까지 같이 없어져서 10회 제한이 의미가 없어진다.
     * OCTOMO 방식에서는 인증번호가 맞아도 문자가 아직 도착하지 않았으면 verified=false다(문자를 보낸 뒤 다시 누르면 된다).
     * OCTOMO에 연결하지 못했을 때만 503 VERIFICATION_UNAVAILABLE이고, 이때는 롤백돼서 시도 횟수가 늘지 않는다
     * (사용자 잘못이 아니므로).
     */
    @Transactional
    public SignupPhoneConfirmResponse confirm(Long verificationId, String code, String rawPhone) {
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

        // 해시 문자열 비교도 일정 시간에 끝나는 방식으로 (비교 시간 차이로 값을 추측하는 공격 방지)
        boolean match = MessageDigest.isEqual(
                tokenHasher.sha256Hex(code).getBytes(StandardCharsets.UTF_8),
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8));
        // 시도 횟수는 "인증번호가 틀렸을 때만" 센다. 인증번호를 직접 입력하는 방식(mock, 문자 발송)에서는 이것이 맞히기 시도를 막는
        // 장치이고, OCTOMO 방식에서는 앱이 서버가 알려 준 인증번호를 그대로 보내므로 틀릴 일이 없다.
        if (!match) {
            verification.increaseAttempt();
            return new SignupPhoneConfirmResponse(false, null, null);
        }

        OctomoClient octomo = octomoProvider.getIfAvailable();
        if (octomo != null) {
            // OCTOMO 방식: 통과 여부는 "그 번호에서 문자가 왔는가"로 정해지고 인증번호를 맞히는 게 아니다. 그래서 문자를 기다리며
            // 앱이 반복해서 보내는 확인은 시도 횟수로 세지 않는다(세면 2초마다 확인하는 앱이 30초 만에 10회를 채워 "횟수 초과"가 된다).
            // 대신 OCTOMO 호출이 폭주하지 않게, 같은 인증은 1.5초에 한 번만 OCTOMO에 묻고 그보다 잦은 확인은 "아직"이라고 답한다.
            // 인증번호는 5분 뒤 만료되므로 이 반복 확인도 그 안으로 한정된다.
            if (octomoCheckThrottle.tooSoon(verificationId, System.currentTimeMillis())
                    || !messageArrived(octomo, verification, code, rawPhone)) {
                return new SignupPhoneConfirmResponse(false, null, null);
            }
            octomoCheckThrottle.forget(verificationId);
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

    /**
     * 사용자가 인증번호를 대표번호로 문자로 보냈는지 OCTOMO에 조회한다. 조회할 번호는 요청에 다시 실어 보낸 값이고,
     * 인증을 요청한 번호(해시)와 같을 때만 조회한다 - 다른 번호를 조회해서 남의 번호 인증을 통과하는 걸 막는다.
     * 조회 범위는 인증을 요청한 이후로 한정한다(VerificationWindow).
     */
    private boolean messageArrived(OctomoClient octomo, PhoneVerification verification, String code, String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (!PhoneNumbers.isValidMobile(phone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT, "인증할 휴대폰 번호를 함께 보내주세요.");
        }
        boolean samePhone = MessageDigest.isEqual(
                phoneHasher.hash(phone).getBytes(StandardCharsets.UTF_8),
                verification.getPhoneHash().getBytes(StandardCharsets.UTF_8));
        if (!samePhone) {
            return false;
        }
        int withinMinutes = VerificationWindow.withinMinutes(verification.getCreatedAt(), LocalDateTime.now());
        try {
            return octomo.messageExists(phone, code, withinMinutes);
        } catch (OctomoUnavailableException e) {
            // 원인 종류와 상태 코드만 남는다 (번호·인증번호·API Key는 남지 않는다)
            log.warn("OCTOMO 조회 실패: verification_id={}, 원인={}", verification.getVerificationId(), e.getMessage());
            throw new BusinessException(ErrorCode.VERIFICATION_UNAVAILABLE);
        }
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}