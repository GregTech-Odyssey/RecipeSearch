package com.gto.recipesearch;

import java.util.Iterator;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Small iterator utilities: lazily materialized, filtered and concatenated iterators plus
 * an {@link Iterable} adapter.
 */
@SuppressWarnings("unused")
public class IteratorUtil {

    public static <T> Iterable<T> asIterable(Iterator<T> iterator) {
        return () -> iterator;
    }

    public static <T> Iterator<T> lazy(Supplier<Iterator<T>> iteratorSupplier) {
        return new Iterator<>() {

            private Iterator<T> iterator = null;

            @Override
            public boolean hasNext() {
                if (iterator == null) iterator = iteratorSupplier.get();
                return iterator.hasNext();
            }

            @Override
            public T next() {
                return iterator.next();
            }
        };
    }

    public static <T> Iterator<T> filter(Iterator<T> iterator, Predicate<T> predicate) {
        return new Iterator<>() {

            private T next;

            @Override
            public boolean hasNext() {
                while (iterator.hasNext()) {
                    next = iterator.next();
                    if (predicate.test(next)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public T next() {
                return next;
            }
        };
    }

    public static <T> Iterator<T> concat(Iterator<T> first, Iterator<T> second) {
        return new Iterator<>() {
            private Iterator<? extends T> current = first;

            @Override
            public boolean hasNext() {
                if (current.hasNext()) return true;
                if (current == first) current = second;
                return current.hasNext();
            }

            @Override
            public T next() {
                return current.next();
            }

            @Override
            public void remove() {
                current.remove();
            }
        };
    }
}
