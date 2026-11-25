package com.gamcho.yakyeon.domain.device.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 출고 약통 목록(device_inventory). 약통을 만들 때 시리얼번호와 정답 비밀값의 SHA-256 해시를 미리 넣어 두고(DB 담당),
 * 서버는 읽기만 한다. 비밀값 원문은 이 목록에도 없다.
 */
@Repository
@RequiredArgsConstructor
public class DeviceInventoryRepository {

    static final String FIND_SECRET_HASH_SQL = "SELECT secret_hash FROM device_inventory WHERE serial_no = :serialNo";

    private final NamedParameterJdbcTemplate jdbc;

    /** 이 시리얼의 정답 비밀값 해시. 출고 목록에 없으면 비어 있다 */
    public Optional<String> findSecretHash(String serialNo) {
        return jdbc.query(FIND_SECRET_HASH_SQL, new MapSqlParameterSource("serialNo", serialNo),
                        (rs, n) -> rs.getString(1))
                .stream().findFirst();
    }
}