package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * FR-AUTH-001~006
 *
 * login_id는 DB에 uq_app_user_login_id_active(활성 계정끼리만 유니크)로 걸려 있어서,
 * 탈퇴(deleted_at IS NOT NULL)한 계정의 아이디는 재사용 가능해야 한다.
 * 그래서 중복 확인은 반드시 "활성 계정 중에서만" 검사해야 하며, 단순 existsByLoginId를
 * 쓰면 탈퇴 계정까지 걸려서 정상적인 재가입이 막힌다.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 로그인 시 사용 — 활성 계정만 조회 */
    Optional<AppUser> findByLoginIdAndDeletedAtIsNull(String loginId);

    /** 회원가입 시 아이디 중복 확인 — 활성 계정 기준 */
    boolean existsByLoginIdAndDeletedAtIsNull(String loginId);

    /**
     * 비밀번호 변경·계정 삭제 시 사용 — 토큰 발급 이후 다른 세션에서 이미 탈퇴 처리된
     * 계정이 남은 Access Token으로 또 접근하는 걸 막기 위해 deletedAt 조건을 같이 건다.
     */
    Optional<AppUser> findByUserIdAndDeletedAtIsNull(Long userId);
}