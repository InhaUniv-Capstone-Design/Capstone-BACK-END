package com.gamcho.yakyeon.domain.account.dto;

/**
 * REST API 명세 §1 GET /auth/check-id 응답: { available: boolean }
 */
public record CheckIdResponse(boolean available) {
}