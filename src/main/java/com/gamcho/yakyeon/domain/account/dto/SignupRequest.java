package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.account.entity.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

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
}