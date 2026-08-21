package com.gto.recipesearch;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Iterative (non-recursive) searcher over the recipe trie.
 *
 * <p>The search keeps an explicit frame stack ({@link SearchFrame}) instead of using the
 * JVM call stack, so deep tries never overflow and frames can be pooled and reused across
 * searches. Each frame records which input indices have already been explored in a skip
 * bit-set, so backtracking never re-visits the same input while exploring an alternative
 * subtree.
 *
 * <p>This class implements {@link Iterator} and {@link Iterable} ({@link #iterator()}
 * returns itself), so a search result can be consumed via a for-each loop, a plain
 * {@code hasNext()/next()} loop, {@link #forEach} or {@link #stream()}. A searcher holds
 * mutable traversal state, so one instance can only be used for a single traversal.
 */
@SuppressWarnings("unused")
public class RecipeSearcher<R> implements Iterator<R>, Iterable<R> {

    Predicate<R> predicate;

    Node<R> drainingNode;
    int recipeCursor;

    private R next;
    private boolean hasNext;
    int maxDepth;
    int depth;
    IntLongMap available;
    int[] inputKeys;
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
    public RecipeSearcher(int expectedDepth, Branch<R> branch, IntLongMap available, int[] inputKeys, Predicate<R> predicate, Iterator<R> fallback) {
        // maxDepth 至少为 1：帧栈永远保留根帧，否则全空配方库（maxSearchDepth=0）会越界
        this.maxDepth = Math.max(1, expectedDepth);
        frames = new SearchFrame[this.maxDepth];
        for (int i = 0; i < this.maxDepth; i++) {
            frames[i] = new SearchFrame<>();
        }
        this.available = available;
        this.inputKeys = inputKeys;
        this.predicate = predicate;
        this.fallback = fallback;
        int length = inputKeys.length;
        frames[0].push(branch, length, this.maxDepth);
        hasNext = length > 0;
    }

    /**
     * Doubles the frame stack capacity when a search descends deeper than expected.
     */
    void expansion() {
        this.maxDepth *= 2;
        this.frames = Arrays.copyOf(this.frames, this.maxDepth);
        for (int i = this.depth; i < this.maxDepth; i++) {
            this.frames[i] = new SearchFrame<>();
        }
    }

    /**
     * Re-point this pooled searcher at a new query so the instance can be reused.
     */
    public void reset(int expectedDepth, Branch<R> branch, IntLongMap available, int[] inputKeys, Predicate<R> predicate, Iterator<R> fallback) {
        this.available = available;
        this.inputKeys = inputKeys;
        this.predicate = predicate;
        this.fallback = fallback;
        drainingNode = null;
        recipeCursor = 0;
        depth = 0;
        int length = inputKeys.length;
        frames[0].push(branch, length, expectedDepth);
        hasNext = length > 0;
    }

    /**
     * Returns the first trie match without collecting the rest, or {@code null} when the
     * query misses the trie. Does not consult the fallback iterator (see
     * {@link AbstractRecipeDB#findAnyMatch} for the full fallback chain).
     */
    public R findAny() {
        R r;
        while (depth >= 0) {
            SearchFrame<R> frame = this.frames[depth];
            if (frame.probeByBranch) {
                r = frame.searchByBranch(this);
            } else {
                r = frame.searchByInput(this);
            }
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Advances to the next match: first drains the current multi-recipe node, then walks
     * the frame stack (each frame probes its branch for the remaining inputs), and finally
     * falls back to the linear serial/parallel iterator once the trie is exhausted.
     */
    @Override
    public boolean hasNext() {
        if (hasNext) {
            while (drainingNode != null) {
                next = drainingNode.get(this, null);
                if (next != null) {
                    return true;
                }
            }

            while (depth >= 0) {
                SearchFrame<R> frame = this.frames[depth];
                if (frame.probeByBranch) {
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
            if (frame.probeByBranch) {
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
