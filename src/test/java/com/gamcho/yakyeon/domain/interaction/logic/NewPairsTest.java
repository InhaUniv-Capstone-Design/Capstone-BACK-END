package com.gamcho.yakyeon.domain.interaction.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewPairsTest {

    private static NewPairs.PairKey pair(long a, long b, String seq) {
        return NewPairs.keyOf(a, b, seq);
    }

    @Test
    @DisplayName("직전 검사가 없으면(처음) 모든 쌍이 새 쌍이다")
    void firstCheckEverythingIsNew() {
        List<NewPairs.PairKey> result = NewPairs.newPairs(List.of(), List.of(pair(1, 2, "D1"), pair(1, 3, "D2")));
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("직전에도 있던 쌍은 새 쌍이 아니다 (약을 하나 고칠 때마다 같은 알림이 가지 않게)")
    void samePairsAreNotNew() {
        List<NewPairs.PairKey> before = List.of(pair(1, 2, "D1"));
        List<NewPairs.PairKey> now = List.of(pair(1, 2, "D1"));
        assertThat(NewPairs.newPairs(before, now).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("약을 새로 등록해서 생긴 쌍만 새 쌍이다")
    void onlyTheAddedPairIsNew() {
        List<NewPairs.PairKey> before = List.of(pair(1, 2, "D1"));
        List<NewPairs.PairKey> now = List.of(pair(1, 2, "D1"), pair(2, 5, "D9"));
        List<NewPairs.PairKey> result = NewPairs.newPairs(before, now);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).durSeq()).isEqualTo("D9");
    }

    @Test
    @DisplayName("쌍이 사라지기만 하면(약을 지우거나 종료) 새 쌍이 없다")
    void removedPairsAreNotNew() {
        List<NewPairs.PairKey> before = List.of(pair(1, 2, "D1"), pair(2, 5, "D9"));
        List<NewPairs.PairKey> now = List.of(pair(1, 2, "D1"));
        assertThat(NewPairs.newPairs(before, now).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("같은 약 쌍이라도 다른 등재 번호면 새 쌍이다")
    void differentDurSeqIsNew() {
        assertThat(NewPairs.newPairs(List.of(pair(1, 2, "D1")), List.of(pair(1, 2, "D1"), pair(1, 2, "D2"))).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("약 두 개의 순서가 달라도 같은 쌍으로 본다")
    void orderDoesNotMatter() {
        assertThat(pair(7, 3, "D1")).isEqualTo(pair(3, 7, "D1"));
        assertThat(NewPairs.newPairs(List.of(pair(7, 3, "D1")), List.of(pair(3, 7, "D1"))).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("이번 결과에 같은 쌍이 여러 번 있어도 한 번만 센다")
    void duplicatesInCurrentAreCountedOnce() {
        assertThat(NewPairs.newPairs(List.of(), List.of(pair(1, 2, "D1"), pair(2, 1, "D1"))).size()).isEqualTo(1);
    }
}