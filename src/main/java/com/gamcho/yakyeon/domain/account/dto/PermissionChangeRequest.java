package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gamcho.yakyeon.domain.account.entity.GuardianLink;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/** 보호자 권한 범위 변경 요청 (FR-AUTH-015) */
@Getter
public class PermissionChangeRequest {

    @JsonProperty("permission_scope")
    @NotNull(message = "permission_scope(READ_ONLY 또는 READ_WRITE)가 필요합니다.")
    private GuardianLink.PermissionScope permissionScope;
}