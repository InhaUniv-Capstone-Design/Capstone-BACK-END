package com.gamcho.yakyeon.domain.dose.logic;

import java.util.List;

/** 지울 예정의 번호와 새로 만들 예정 */
public record ReconcileResult(List<Long> deleteIds, List<PlannedDose> inserts) {
}