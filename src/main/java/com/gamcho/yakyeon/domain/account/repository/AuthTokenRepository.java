package com.gamcho.yakyeon.domain.account.repository;

import com.gamcho.yakyeon.domain.account.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * FR-AUTH-006 (자동 로그인 토큰 / Refresh Token)
 */
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    /**
     * Refresh Token 재사용(탈취 의심) 탐지 시, 해당 유저의 살아있는 토큰을 전부 폐기하기 위해 사용.
     * revoked_at이 아직 없는(=아직 유효한) 토큰만 가져온다.
     */
    List<AuthToken> findByUser_UserIdAndRevokedAtIsNull(Long userId);
}