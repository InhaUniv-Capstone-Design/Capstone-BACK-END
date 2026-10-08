package com.gamcho.yakyeon.domain.account.access;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 접근 기록(access_audit_log) 쓰기.
 *
 * 반드시 "별도 트랜잭션"으로 저장한다(REQUIRES_NEW). 같은 트랜잭션에 묶으면, 접근이 거부돼서
 * 예외가 던져지는 순간 롤백과 함께 "거부 기록"도 사라진다 - 거부 기록이 제일 필요한 순간에 없어지는 셈이다.
 * 이 클래스를 같은 클래스 안에서 직접 호출하지 말고 반드시 다른 빈(PatientAccessGuard)을 통해 호출해야
 * REQUIRES_NEW가 적용된다.
 *
 * 기록이 실패하면 예외를 그대로 던져 요청을 실패시킨다(fail-closed). 보호자가 기록 없이 건강정보에
 * 접근하는 일이 없게 하기 위해서다.
 *
 * ip 컬럼이 inet 타입이라 엔티티 대신 SQL로 직접 넣는다. 추가 전용 테이블(수정·삭제 트리거)이라
 * INSERT만 한다.
 */
@Component
@RequiredArgsConstructor
public class AccessAuditWriter {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Long actorUserId, AccessRole actorRole, Long patientId, Long linkId,
                      AccessAction action, String resourceType, Long resourceId, boolean allowed) {
        jdbcTemplate.update(
                "INSERT INTO access_audit_log "
                        + "(actor_user_id, actor_role, patient_id, link_id, action, resource_type, resource_id, result, ip) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS inet))",
                actorUserId, actorRole.name(), patientId, linkId, action.name(),
                resourceType, resourceId, allowed ? "ALLOWED" : "DENIED", currentIp());
    }

    /**
     * 요청한 쪽의 IP. 프록시 헤더(X-Forwarded-For)는 일부러 믿지 않는다 - 클라이언트가 마음대로 위조할 수 있어서
     * 감사 기록의 신뢰성을 해친다. 리버스 프록시 뒤에 배포하면 서버 설정으로 원본 IP를 전달받도록 따로 처리해야 한다.
     */
    private String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}