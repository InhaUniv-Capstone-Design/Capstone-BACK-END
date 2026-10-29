package com.gamcho.yakyeon.domain.interaction.logic;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 병용금기 검사에서 "새로 생긴 쌍"을 가려내는 순수 로직 (스프링·DB 없음).
 *
 * 약을 바꿀 때마다 그 시점의 전체 결과를 새 검사로 저장하므로, 이번 결과 중 직전 성공 검사에 없던 쌍이
 * 새 등재 쌍이다. 같은 쌍이 검사마다 반복해서 나오므로 이렇게 가리지 않으면 약을 하나 고칠 때마다 같은 알림이 간다.
 * 쌍의 정체는 (약 두 개, DUR 등재 번호)다. 약 번호는 작은 쪽이 앞이 되도록 맞춘다(DB 규칙 a &lt; b와 같은 순서).
 */
public final class NewPairs {

    public record PairKey(long medicationAId, long medicationBId, String durSeq) {
    }

    private NewPairs() {
    }

    /** 약 두 개의 순서와 상관없이 같은 쌍은 같은 키가 되게 만든다 */
    public static PairKey keyOf(long medicationAId, long medicationBId, String durSeq) {
        return new PairKey(Math.min(medicationAId, medicationBId), Math.max(medicationAId, medicationBId), durSeq);
    }

    /** 이번 결과 중 직전 결과에 없던 쌍 (같은 쌍이 여러 번 들어 있어도 한 번만) */
    public static List<PairKey> newPairs(Collection<PairKey> previous, Collection<PairKey> current) {
        Set<PairKey> before = new HashSet<>(previous);
        Set<PairKey> result = new LinkedHashSet<>();
        for (PairKey key : current) {
            if (!before.contains(key)) {
                result.add(key);
            }
        }
        return List.copyOf(result);
    }
}