package com.gamcho.yakyeon.domain.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 열람 범위 저장 결과. 필수 3개에 모두 동의하면 status=ACTIVE이고 permission_scope=READ_WRITE로 시작한다.
 * 하나라도 비동의하면 연동은 만들어지지 않고 status=REJECTED, shared_items=[]이며 permission_scope는 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardianGrantResponse(
        @JsonProperty("link_id") Long linkId,
        @JsonProperty("status") String status,
        @JsonProperty("permission_scope") String permissionScope,
        @JsonProperty("shared_items") List<String> sharedItems
) {
}