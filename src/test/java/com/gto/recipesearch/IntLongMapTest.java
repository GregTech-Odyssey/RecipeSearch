package com.gto.recipesearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntLongMapTest {

    @Test
    void addAccumulatesAmounts() {
        IntLongMap map = new IntLongMap();
        map.add(5, 3);
        map.add(5, 4);
        assertEquals(7, map.get(5));
    }

    @Test
    void setOverwritesInsteadOfAccumulating() {
        IntLongMap map = new IntLongMap();
        map.set(5, 3);
        map.set(5, 4);
        assertEquals(4, map.get(5));
    }

    @Test
    void addWithZeroOrZeroKeyIsIgnored() {
        IntLongMap map = new IntLongMap();
        map.add(0, 5);
        map.add(5, 0);
        assertTrue(map.isEmpty());
    }

    @Test
    void addOverflowIsClampedToMaxValue() {
        IntLongMap map = new IntLongMap();
        map.add(5, Long.MAX_VALUE);
        map.add(5, 1);
        assertEquals(Long.MAX_VALUE, map.get(5));
    }

    @Test
    void addAllAccumulatesFromOtherMap() {
        IntLongMap target = new IntLongMap();
        target.add(5, 3);
        IntLongMap source = new IntLongMap();
        source.add(5, 2);
        source.add(7, 1);
        target.addAll(source);
        assertEquals(5, target.get(5));
        assertEquals(1, target.get(7));
    }

    @Test
    void setAllOverwritesFromOtherMap() {
        IntLongMap target = new IntLongMap();
        target.add(5, 3);
        IntLongMap source = new IntLongMap();
        source.add(5, 2);
        target.setAll(source);
        assertEquals(2, target.get(5));
    }

    @Test
    void copyToFillsParallelArraysAndReturnsCount() {
        IntLongMap map = new IntLongMap();
        map.add(5, 3);
        map.add(7, 4);
        int[] keys = new int[2];
        long[] amounts = new long[2];
        int count = map.copyTo(keys, amounts);
        assertEquals(2, count);
        // 顺序无保证，按内容断言
        for (int i = 0; i < count; i++) {
            assertTrue(keys[i] == 5 || keys[i] == 7);
            assertEquals(map.get(keys[i]), amounts[i]);
        }
    }

    @Test
    void copyToEmptyMapReturnsZero() {
        IntLongMap map = new IntLongMap();
        int[] keys = new int[1];
        long[] amounts = new long[1];
        assertEquals(0, map.copyTo(keys, amounts));
    }

    @Test
    void containsKeyAndGet() {
        IntLongMap map = new IntLongMap();
        map.add(5, 3);
        assertTrue(map.containsKey(5));
        assertFalse(map.containsKey(6));
        assertEquals(0, map.get(6));
    }

    @Test
    void emptySingletonIsImmutable() {
        IntLongMap map = IntLongMap.EMPTY;
        assertTrue(map.isEmpty());
        assertEquals(0, map.toIntArray().length);
        map.add(5, 3);      // 静默忽略
        map.set(5, 3);      // 静默忽略
        map.addAll(new IntLongMap());
        map.setAll(new IntLongMap());
        assertTrue(map.isEmpty());
        assertEquals(0, map.get(5));
        assertFalse(map.containsKey(5));
    }

    @Test
    void addAllFromEmptySourceIsNoop() {
        IntLongMap target = new IntLongMap();
        target.add(5, 3);
        target.addAll(new IntLongMap());
        assertEquals(3, target.get(5));
        assertEquals(1, target.size());
    }
}
