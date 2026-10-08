package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PermissionChangeResponse(
        @JsonProperty("guardian_link_id") Long guardianLinkId,
        @JsonProperty("permission_scope") String permissionScope
) {
}