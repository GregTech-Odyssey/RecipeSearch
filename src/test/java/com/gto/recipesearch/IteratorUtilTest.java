package com.gto.recipesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class IteratorUtilTest {

    @Test
    void filterKeepsOnlyMatchingElements() {
        Iterator<Integer> filtered = IteratorUtil.filter(Arrays.asList(1, 2, 3, 4).iterator(), n -> n % 2 == 0);
        List<Integer> out = new ArrayList<>();
        filtered.forEachRemaining(out::add);
        assertEquals(List.of(2, 4), out);
    }

    @Test
    void concatSwitchesAfterFirstExhausted() {
        Iterator<Integer> concat = IteratorUtil.concat(
                Arrays.asList(1, 2).iterator(),
                Arrays.asList(3, 4).iterator());
        List<Integer> out = new ArrayList<>();
        concat.forEachRemaining(out::add);
        assertEquals(List.of(1, 2, 3, 4), out);
    }

    @Test
    void lazyInitializesOnlyOnFirstAccess() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Iterator<Integer>> supplier = () -> {
            calls.incrementAndGet();
            return List.of(1, 2, 3).iterator();
        };
        Iterator<Integer> lazy = IteratorUtil.lazy(supplier);
        assertEquals(0, calls.get());
        assertTrue(lazy.hasNext());
        assertEquals(1, calls.get());
        assertEquals(Integer.valueOf(1), lazy.next());
        assertEquals(Integer.valueOf(2), lazy.next());
        assertEquals(Integer.valueOf(3), lazy.next());
        assertFalse(lazy.hasNext());
        assertEquals(1, calls.get());
    }

    @Test
    void asIterableAdaptsIterator() {
        Iterable<Integer> iterable = IteratorUtil.asIterable(List.of(5, 6).iterator());
        List<Integer> out = new ArrayList<>();
        iterable.forEach(out::add);
        assertEquals(List.of(5, 6), out);
    }

    @Test
    void filterOfEmptyIteratorIsEmpty() {
        Iterator<Integer> filtered = IteratorUtil.filter(List.<Integer>of().iterator(), n -> true);
        assertFalse(filtered.hasNext());
    }
}
