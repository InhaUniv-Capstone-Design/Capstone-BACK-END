package com.gamcho.yakyeon.domain.notification.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 약통이 보고한 칸 상태(device_compartment_state)와 푸시 받을 기기 토큰을 읽는다. 상태 표는 중계 서버가 채우고 여기서는 읽기만 한다.
 */
@Repository
public class PouchStateRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PouchStateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 이 칸의 저장된 pouch_present. 아직 한 번도 보고되지 않은 칸이면 비어 있다 */
    public Optional<Boolean> findPouchPresent(long deviceId, int compartmentNo) {
        List<Boolean> rows = jdbc.queryForList("SELECT pouch_present FROM device_compartment_state "
                        + "WHERE device_id = :deviceId AND compartment_no = :no",
                new MapSqlParameterSource().addValue("deviceId", deviceId).addValue("no", compartmentNo), Boolean.class);
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 이 복약자의 앱 계정. 앱 계정이 없는 복약자(보호자가 대리 등록)면 비어 있다 */
    public Optional<Long> findPatientUserId(long patientId) {
        List<Long> rows = jdbc.queryForList("SELECT user_id FROM patient WHERE patient_id = :patientId AND user_id IS NOT NULL",
                new MapSqlParameterSource("patientId", patientId), Long.class);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 이 계정으로 로그인한 기기들의 FCM 토큰 (알림 허용 여부와 상관없이 데이터 푸시는 보낸다) */
    public List<String> tokensOf(long userId) {
        return jdbc.queryForList("SELECT fcm_token FROM push_token WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), String.class);
    }

    /** 무효로 확인된 토큰을 지운다 (앱 삭제·토큰 만료). 다시 보내도 소용없다 */
    public void deleteToken(String fcmToken) {
        jdbc.update("DELETE FROM push_token WHERE fcm_token = :token", new MapSqlParameterSource("token", fcmToken));
    }
}