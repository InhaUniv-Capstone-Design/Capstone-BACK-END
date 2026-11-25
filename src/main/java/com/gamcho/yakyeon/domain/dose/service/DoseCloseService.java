package com.gamcho.yakyeon.domain.dose.service;

import com.gamcho.yakyeon.common.util.ApiTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 마감이 지난 판정 전 예정을 누락(MISSED)으로 확정한다 (DB 함수 close_missed_doses).
 *
 * 이 함수가 없으면 사용자가 아무것도 하지 않은 예정은 영원히 "예정"으로 남아서 홈 화면의 누락 표시와
 * 복용 기록이 비어 있게 된다. 판정은 SYSTEM으로 기록되고 복용 시각은 없다.
 *
 * 사용자가 같은 예정을 동시에 기록해도 안전하다 - 기록하는 쪽이 예정 행을 잠그고 있으면 이 함수는 잠금이 풀릴 때까지
 * 기다렸다가, 이미 판정된 예정은 건드리지 않는다 (동시 실행으로 확인했다).
 */
@Service
@RequiredArgsConstructor
public class DoseCloseService {

    private final JdbcTemplate jdbcTemplate;

    /** @return 이번에 누락으로 확정한 예정 수 */
    @Transactional
    public long closeMissed() {
        Long count = jdbcTemplate.queryForObject("SELECT close_missed_doses(CAST(? AS timestamptz))", Long.class,
                OffsetDateTime.now(ApiTime.SERVICE_ZONE));
        return count == null ? 0 : count;
    }
}