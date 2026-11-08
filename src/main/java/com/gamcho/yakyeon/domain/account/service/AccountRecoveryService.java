package com.gamcho.yakyeon.domain.account.service;

import com.gamcho.yakyeon.common.exception.BusinessException;
import com.gamcho.yakyeon.common.exception.ErrorCode;
import com.gamcho.yakyeon.common.util.PhoneNumbers;
import com.gamcho.yakyeon.domain.account.dto.FindIdRequest;
import com.gamcho.yakyeon.domain.account.dto.FindIdResponse;
import com.gamcho.yakyeon.domain.account.dto.PasswordResetRequest;
import com.gamcho.yakyeon.domain.account.logic.AccountRecoveryRules;
import com.gamcho.yakyeon.domain.account.repository.AccountRecoveryRepository;
import com.gamcho.yakyeon.domain.account.repository.AccountRecoveryRepository.Account;
import com.gamcho.yakyeon.security.PhoneHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 아이디 찾기와 비밀번호 재설정 (로그인할 수 없는 사람이 쓴다).
 *
 * 본인 확인: 아이디·이름·번호는 남도 알 수 있어서, "그 번호가 지금 이 사람 손에 있다"는 문자 인증(가입 때와 같은 방식,
 * SignupVerificationService)을 마친 증표가 있어야만 처리한다. 증표는 번호에 묶여 있어서, 인증받은 번호와 다른 번호의 계정은
 * 절대 건드릴 수 없다.
 *
 * 증표 처리: 먼저 증표를 확인하며 소진(consume)하고, 그다음 계정을 찾는다. 일치하는 계정이 없어서 예외가 나면
 * 이 트랜잭션이 롤백되면서 소진도 취소되므로, 오타를 고쳐서 문자 인증을 다시 하지 않고 재시도할 수 있다.
 * 일치하는 계정이 있을 때만 증표가 사라진다(한 번 쓴 증표는 다시 못 쓴다).
 *
 * 열거 방지: 없는 아이디, 다른 번호의 계정, 이름 불일치는 같은 오류(ACCOUNT_NOT_FOUND)로 응답한다.
 * 비밀번호는 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private final SignupVerificationService signupVerificationService;
    private final AccountRecoveryRepository accountRecoveryRepository;
    private final PhoneHasher phoneHasher;
    private final PasswordEncoder passwordEncoder;

    /** 이 번호로 가입한 계정 중 이름이 같은 것들의 아이디 */
    @Transactional
    public FindIdResponse findId(FindIdRequest request) {
        String phoneHash = verifiedPhoneHash(request.getPhone(), request.getVerificationId(), request.getVerificationToken());

        List<FindIdResponse.FoundAccount> accounts = accountRecoveryRepository.findActiveByPhoneHash(phoneHash).stream()
                .filter(a -> AccountRecoveryRules.sameName(a.name(), request.getName()))
                .map(a -> new FindIdResponse.FoundAccount(a.loginId(), a.accountType()))
                .toList();
        if (accounts.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return new FindIdResponse(accounts);
    }

    /**
     * 비밀번호를 새로 정한다. 성공하면 이 계정의 모든 로그인 세션을 끝내고(이전 비밀번호로 만든 세션이 남지 않게),
     * 로그인 잠금을 풀고, 알림 기기 등록을 지운다. 사용자는 새 비밀번호로 다시 로그인한다.
     */
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        String phoneHash = verifiedPhoneHash(request.getPhone(), request.getVerificationId(), request.getVerificationToken());

        Account account = accountRecoveryRepository.findActiveByLoginId(request.getLoginId())
                .filter(a -> samePhone(a.phoneHash(), phoneHash))
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!accountRecoveryRepository.resetPassword(account.userId(), passwordEncoder.encode(request.getNewPassword()))) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);   // 그 사이 탈퇴한 계정
        }
        accountRecoveryRepository.revokeAllSessions(account.userId());
        accountRecoveryRepository.deletePushTokens(account.userId());
        log.info("비밀번호 재설정 완료: user_id={}", account.userId());
    }

    /** 요청의 번호가 인증받은 번호와 같은지 확인하며 증표를 소진하고, 그 번호의 해시를 돌려준다 */
    private String verifiedPhoneHash(String rawPhone, Long verificationId, String verificationToken) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (!PhoneNumbers.isValidMobile(phone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
        String phoneHash = phoneHasher.hash(phone);
        signupVerificationService.consume(verificationId, verificationToken, phoneHash);
        return phoneHash;
    }

    /** 해시 비교도 일정 시간에 끝나는 방식으로 */
    private static boolean samePhone(String storedHash, String enteredHash) {
        return storedHash != null && MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8), enteredHash.getBytes(StandardCharsets.UTF_8));
    }
}