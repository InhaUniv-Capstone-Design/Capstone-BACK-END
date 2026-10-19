package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API 명세 §1 POST /auth/signup 요청
 * FR-AUTH-001~004, FR-AUTH-008(약관 동의)
 *
 * accountType은 문자열(SELF|GUARDIAN)이 아니라 AppUser.AccountType enum(PATIENT|GUARDIAN)으로
 * 받는다. 이렇게 하면 잘못된 값이 들어왔을 때 서비스 코드까지 가기 전에
 * Jackson 역직렬화 단계에서 바로 400으로 걸러진다.
 *
 * agreements는 회원가입 화면에 표시된 약관(TOS/PRIVACY/SENSITIVE_HEALTH/SERVICE_NOTICE)에
 * 대한 동의 여부 목록이다. 필수 약관 중 하나라도 agreed=false거나 목록에 아예 없으면
 * AuthService에서 REQUIRED_TERMS_NOT_AGREED로 거부된다.
 */
@Getter
public class SignupRequest {

    @JsonProperty("login_id")
    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(min = 4, max = 30, message = "아이디는 4~30자여야 합니다.")
    // 팀 DB는 '#'로 시작하는 아이디를 탈퇴 계정용으로 예약해 둔다 (CHECK 제약) - 가입 단계에서 미리 막는다
    @Pattern(regexp = "^[^#].*$", message = "아이디는 #으로 시작할 수 없습니다.")
    private String loginId;

    @JsonProperty("password")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    private String password;

    @JsonProperty("password_confirm")
    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    private String passwordConfirm;

    @JsonProperty("account_type")
    @NotNull(message = "계정 유형을 선택해주세요. (PATIENT 또는 GUARDIAN)")
    private AppUser.AccountType accountType;

    @JsonProperty("agreements")
    @NotEmpty(message = "약관 동의 내역을 입력해주세요.")
    @Valid
    private List<AgreementItem> agreements;

    // ---- 계정 프로필 (회원가입 화면의 이름·생년월일·휴대폰). 이메일은 받지 않는다 ----

    /** 본인(PATIENT) 가입이면 복약자 이름으로도 쓰인다 */
    @JsonProperty("name")
    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    @JsonProperty("birth_date")
    @NotNull(message = "생년월일을 입력해주세요.")
    @Past(message = "생년월일이 올바르지 않습니다.")
    private LocalDate birthDate;

    /** 하이픈 없이 숫자만 (예: 01012345678). 서버에서 암호화해 저장하고 평문은 남기지 않는다 */
    @JsonProperty("phone")
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다. (예: 01012345678)")
    private String phone;

    /** POST /auth/phone-verifications 응답의 verification_id */
    @JsonProperty("verification_id")
    @NotNull(message = "휴대폰 인증을 완료해주세요.")
    private Long verificationId;

    /** POST /auth/phone-verifications/{id}/confirm 성공 응답의 verification_token */
    @JsonProperty("verification_token")
    @NotBlank(message = "휴대폰 인증을 완료해주세요.")
    @Size(max = 300, message = "인증 정보가 올바르지 않습니다.")
    private String verificationToken;
}