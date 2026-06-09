package com.gto.recipesearch;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class IteratorUtil {

    public static <T> Iterable<T> wrap(Iterator<T> iterator) {
        return () -> iterator;
    }

    public static <T> Iterator<T> lazy(Supplier<Iterator<T>> iteratorSupplier) {
        return new Iterator<T>() {

            private Iterator<T> iterator = null;

            @Override
            public boolean hasNext() {
                if (iterator == null) iterator = iteratorSupplier.get();
                return iterator.hasNext();
            }

            @Override
            public T next() {
                if (iterator == null) iterator = iteratorSupplier.get();
                return iterator.next();
            }
        };
    }

    public static <T, R> Iterator<R> map(Iterator<T> iterator, Function<T, R> mapFunction) {
        return new Iterator<R>() {

            private R next;
            private boolean hasNext;

            @Override
            public boolean hasNext() {
                if (hasNext) return true;
                while (iterator.hasNext()) {
                    next = mapFunction.apply(iterator.next());
                    if (next != null) {
                        hasNext = true;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public R next() {
                if (!hasNext()) throw new NoSuchElementException();
                hasNext = false;
                return next;
            }
        };
    }

    public static <T> Iterator<T> filter(Iterator<T> iterator, Predicate<T> predicate) {
        return new Iterator<T>() {

            private T next;
            private boolean hasNext;

            @Override
            public boolean hasNext() {
                if (hasNext) return true;
                while (iterator.hasNext()) {
                    next = iterator.next();
                    if (predicate.test(next)) {
                        hasNext = true;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                hasNext = false;
                return next;
            }
        };
    }

    public static <T> Iterator<T> concat(Iterator<T> first, Iterator<T> second) {
        return new Iterator<T>() {
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
