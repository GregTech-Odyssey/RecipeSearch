import com.gto.recipesearch.IteratorUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IteratorUtilTest {

    @Test
    void lazyNextInitializesIterator() {
        Iterator<Integer> iterator = IteratorUtil.lazy(() -> Arrays.asList(1, 2).iterator());

        assertEquals(1, iterator.next());
        assertEquals(2, iterator.next());
    }

    @Test
    void mapNextAdvancesWithoutCallingHasNextFirst() {
        Iterator<Integer> iterator = IteratorUtil.map(Arrays.asList(1, 2).iterator(), value -> value * 10);

        assertEquals(10, iterator.next());
        assertEquals(20, iterator.next());
    }

    @Test
    void filterHasNextIsIdempotentUntilNextConsumesCachedValue() {
        AtomicInteger predicateCalls = new AtomicInteger();
        Iterator<Integer> iterator = IteratorUtil.filter(Arrays.asList(1, 2, 3, 4).iterator(), value -> {
            predicateCalls.incrementAndGet();
            return value % 2 == 0;
        });

        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
        assertEquals(2, iterator.next());
        assertEquals(2, predicateCalls.get());
        assertTrue(iterator.hasNext());
        assertEquals(4, iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void filterNextAdvancesWithoutCallingHasNextFirst() {
        Iterator<Integer> iterator = IteratorUtil.filter(Arrays.asList(1, 2, 3, 4).iterator(), value -> value % 2 == 0);

        assertEquals(2, iterator.next());
        assertEquals(4, iterator.next());
    }

    @Test
    void mapHasNextIsIdempotentUntilNextConsumesCachedValue() {
        List<String> values = Arrays.asList("a", "b");
        Iterator<String> iterator = IteratorUtil.map(values.iterator(), value -> value.toUpperCase());

        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
        assertEquals("A", iterator.next());
        assertEquals("B", iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void mapStillSkipsNullResults() {
        Iterator<String> iterator = IteratorUtil.map(Arrays.asList("skip", "keep").iterator(), value -> {
            if ("skip".equals(value)) {
                return null;
            }
            return value.toUpperCase();
        });

        assertEquals("KEEP", iterator.next());
        assertFalse(iterator.hasNext());
    }
}
