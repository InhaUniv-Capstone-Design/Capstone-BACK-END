package com.gamcho.yakyeon.domain.interaction.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 병용금기로 이어진 약들을 묶음(연결 요소)으로 나눈다.
 * 약이 많아야 수십 개라 별도 그래프 엔진 없이 서버에서 계산한다.
 * 묶음은 위험 수준이 아니라 "서로 이어진 구조"만 보여주는 값이다.
 */
public final class InteractionGraphs {

    private InteractionGraphs() {
    }

    /**
     * @param edges 각 원소는 {약 A 번호, 약 B 번호}
     * @return 약이 2개 이상 이어진 묶음만. 묶음 안은 번호 오름차순, 묶음끼리는 가장 작은 번호 순
     */
    public static List<List<Long>> connectedGroups(Collection<long[]> edges) {
        Map<Long, Long> parent = new HashMap<>();
        for (long[] e : edges) {
            union(parent, e[0], e[1]);
        }

        Map<Long, TreeSet<Long>> byRoot = new TreeMap<>();
        for (Long node : new ArrayList<>(parent.keySet())) {
            byRoot.computeIfAbsent(find(parent, node), k -> new TreeSet<>()).add(node);
        }

        List<List<Long>> groups = new ArrayList<>();
        for (TreeSet<Long> members : byRoot.values()) {
            if (members.size() >= 2) {
                groups.add(new ArrayList<>(members));
            }
        }
        groups.sort(Comparator.comparingLong(g -> g.get(0)));
        return groups;
    }

    private static void union(Map<Long, Long> parent, long a, long b) {
        parent.putIfAbsent(a, a);
        parent.putIfAbsent(b, b);
        long ra = find(parent, a);
        long rb = find(parent, b);
        if (ra != rb) {
            parent.put(Math.max(ra, rb), Math.min(ra, rb));
        }
    }

    /** 반복문으로 찾고 경로를 줄인다 (재귀를 쓰지 않아 긴 사슬에서도 안전) */
    private static long find(Map<Long, Long> parent, long x) {
        long root = x;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        long cur = x;
        while (parent.get(cur) != root) {
            long next = parent.get(cur);
            parent.put(cur, root);
            cur = next;
        }
        return root;
    }
}