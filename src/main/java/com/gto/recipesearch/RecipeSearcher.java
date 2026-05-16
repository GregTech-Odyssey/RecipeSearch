package com.gto.recipesearch;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SuppressWarnings("unused")
public class RecipeSearcher<R> implements Iterator<R>, Iterable<R> {

    Predicate<R> predicate;

    Node<R> node;
    int count;

    private R next;
    private boolean hasNext;
    int maxDepth;
    int depth;
    IntLongMap map;
    int[] ints;
    SearchFrame<R>[] frames;
    private Iterator<R> fallback;

    public RecipeSearcher() {
        this(1);
    }

    @SuppressWarnings("unchecked")
    public RecipeSearcher(int expectedDepth) {
        this.maxDepth = expectedDepth;
        frames = new SearchFrame[expectedDepth];
        for (int i = 0; i < expectedDepth; i++) {
            frames[i] = new SearchFrame<>();
        }
    }

    @SuppressWarnings("unchecked")
    public RecipeSearcher(int expectedDepth, Branch<R> branch, IntLongMap map, int[] ints, Predicate<R> predicate, Iterator<R> fallback) {
        this.maxDepth = expectedDepth;
        frames = new SearchFrame[expectedDepth];
        for (int i = 0; i < expectedDepth; i++) {
            frames[i] = new SearchFrame<>();
        }
        this.map = map;
        this.ints = ints;
        this.predicate = predicate;
        this.fallback = fallback;
        int length = ints.length;
        frames[0].push(branch, length, expectedDepth);
        hasNext = length > 0;
    }

    void expansion() {
        this.maxDepth *= 2;
        this.frames = Arrays.copyOf(this.frames, this.maxDepth);
        for (int i = this.depth; i < this.maxDepth; i++) {
            this.frames[i] = new SearchFrame<>();
        }
    }

    public void reset(int expectedDepth, Branch<R> branch, IntLongMap map, int[] ints, Predicate<R> predicate, Iterator<R> fallback) {
        this.map = map;
        this.ints = ints;
        this.predicate = predicate;
        this.fallback = fallback;
        node = null;
        count = 0;
        depth = 0;
        int length = ints.length;
        frames[0].push(branch, length, expectedDepth);
        hasNext = length > 0;
    }

    public R findAny() {
        R r;
        while (depth >= 0) {
            SearchFrame<R> frame = this.frames[depth];
            if (frame.branchProbe) {
                r = frame.searchByBranch(this);
            } else {
                r = frame.searchByInput(this);
            }
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public boolean hasNext() {
        if (hasNext) {
            while (node != null) {
                next = node.get(this, null);
                if (next != null) {
                    return true;
                }
            }

            while (depth >= 0) {
                SearchFrame<R> frame = this.frames[depth];
                if (frame.branchProbe) {
                    next = frame.searchByBranch(this);
                } else {
                    next = frame.searchByInput(this);
                }
                if (next != null) return true;
            }

            if (fallback != null && fallback.hasNext()) {
                next = fallback.next();
                return true;
            }
            hasNext = false;
        }
        return false;
    }

    @Override
    public R next() {
        return next;
    }

    @Override
    public Iterator<R> iterator() {
        return this;
    }

    @Override
    public void forEach(Consumer<? super R> action) {
        while (depth >= 0) {
            SearchFrame<R> frame = this.frames[depth];
            if (frame.branchProbe) {
                frame.forEachByBranch(this, action);
            } else {
                frame.forEachByInput(this, action);
            }
        }
        if (fallback != null) while (fallback.hasNext()) action.accept(fallback.next());
    }

    @Override
    public Spliterator<R> spliterator() {
        return Spliterators.spliteratorUnknownSize(this, 0);
    }

    @Override
    public void forEachRemaining(Consumer<? super R> action) {
        while (hasNext()) action.accept(next);
    }

    public Stream<R> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
