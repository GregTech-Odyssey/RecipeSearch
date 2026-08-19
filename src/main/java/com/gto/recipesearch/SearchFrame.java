package com.gto.recipesearch;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Traversal frame used by {@link RecipeSearcher}. Keeps, per frame, a bit-set of input
 * indices that should be skipped to avoid re-visiting already-handled positions.
 * <p>
 * The {@code skip} bit-set marks every input index that has already been explored so the
 * search does not fall back onto the same inputs while backtracking up the recipe tree
 * (each {@link Node} push consumes one more input and records it in the skip set).
 * <p>
 * The skip set is stored inline as {@code long} + {@code long[]} so the hot search loops
 * are straight-line bit ops — no wrapper object, no virtual-call indirection:
 * <ul>
 *   <li>single-word mode: {@code skipArr == null}, {@code skipWord} holds word 0
 *       (only input indices 0..63 are representable; a full word with {@code 0} means
 *       "nothing skipped for this frame").</li>
 *   <li>multi-word mode: {@code skipArr != null}, one {@code long} per 64 indices.</li>
 * </ul>
 * Because {@code SearchFrame}s are pooled and reused by {@link RecipeSearcher}, every
 * {@code push} rebuilds this frame's skip set from scratch (deriving it from
 * {@code prev}) — the storage is only ever mutated during {@code push}, so the search /
 * forEach loops below treat it as immutable and can read it race-free across frames.
 */
public final class SearchFrame<R> {

    /** Shared empty array used as the starting buffer for multi-word skip frames. */
    private static final long[] EMPTY_LONGS = new long[0];

    boolean branchProbe;
    int index;

    // inline skip bit-set
    boolean multi;      // true = multi-word (skipArr != null), false = single-word
    long skipWord;
    long[] skipArr;

    private Branch<R> branch;

    /**
     * Prepare this frame for the branch reached after consuming one more input.
     *
     * @param branch the next branch to descend into
     * @param size   remaining input count at this depth
     * @param prev   the frame one level up; its skip set (plus the index it just consumed
     *               when it did not probe as a branch) becomes this frame's skip set
     */
    void push(Branch<R> branch, int size, SearchFrame<R> prev) {
        this.branchProbe = size > branch.size();
        this.index = 0;
        if (prev.branchProbe) {
            // The previous level probed via branch.key()/map rather than consuming an
            // exact input index, so there is nothing new to skip — reuse its set as-is
            // (treated read-only for the life of this frame).
            this.skipWord = prev.skipWord;
            this.skipArr = prev.skipArr;
            this.multi = prev.multi;
        } else {
            // The previous level consumed ints[prev.index]; skip that exact index so the
            // search does not re-pick it while exploring the alternative inputs below.
            long pw = prev.skipWord;
            if (prev.multi) {
                // prev is multi-word: give this frame its own buffer. Prefer clone() when
                // the index already fits (no realloc/zero-fill — faster than copyOf);
                // only copy + extend when the consumed index needs a longer array.
                int w = prev.index >>> 6;
                if (w < prev.skipArr.length) {
                    this.skipArr = prev.skipArr.clone();
                } else {
                    this.skipArr = Arrays.copyOf(prev.skipArr, w + 1);
                }
                this.skipWord = pw;
                this.skipArr[w] |= 1L << (prev.index & 0x3F);
                this.multi = true;
            } else {
                // prev is single-word; single-word mode was chosen once at the root push
                // via expectedDepth, so every index here is < 64 — a plain OR, no
                // allocation, no long->long[] upgrade is ever needed.
                this.skipWord = pw | (1L << prev.index);
                this.skipArr = null;
                this.multi = false;
            }
        }
        this.branch = branch;
    }

    /**
     * Prepare this frame for the very first (root) branch of a search.
     *
     * @param branch        root branch
     * @param size          number of input indices being searched
     * @param expectedDepth expected recursion depth (drives whether single- or
     *                      multi-word skip storage is used)
     */
    void push(Branch<R> branch, int size, int expectedDepth) {
        this.branchProbe = size > branch.size();
        this.index = 0;
        if (expectedDepth > 64) {
            // input indices may exceed 63 — start in multi-word mode
            this.skipWord = 0;
            this.skipArr = EMPTY_LONGS;
            this.multi = true;
        } else {
            // all indices fit in a single word
            this.skipWord = 0;
            this.skipArr = null;
            this.multi = false;
        }
        this.branch = branch;
    }

    /**
     * Search this frame by walking the input indices in {@code searcher.ints}.
     * <p>
     * The skip mode is resolved <em>once</em> up front, then a dedicated single-word or
     * multi-word loop runs inline — the per-index check is a plain bit test (no helper
     * method call, no per-iteration branch on the storage mode).
     *
     * @return the first matched result, or {@code null} when this frame is exhausted
     */
    R searchByInput(RecipeSearcher<R> searcher) {
        final int[] ints = searcher.ints;
        final int size = ints.length;
        final Branch<R> branch = this.branch;
        final int depth = searcher.depth;
        if (multi) {
            // multi-word path
            final long[] arr = skipArr;
            while (index < size) {
                int w = index >>> 6;
                if (w >= arr.length || (arr[w] & (1L << (index & 0x3F))) == 0) {
                    Node<R> node = branch.get(ints[index]);
                    if (node != null) {
                        R result = node.get(searcher, this);
                        if (result != null || searcher.depth != depth) {
                            index++;
                            return result;
                        }
                    }
                }
                index++;
            }
        } else {
            // single-word fast path: all representable indices are < 64
            final long word = skipWord;
            while (index < size) {
                if ((word & (1L << index)) == 0) {
                    Node<R> node = branch.get(ints[index]);
                    if (node != null) {
                        R result = node.get(searcher, this);
                        if (result != null || searcher.depth != depth) {
                            index++;
                            return result;
                        }
                    }
                }
                index++;
            }
        }
        searcher.depth--;
        return null;
    }

    R searchByBranch(RecipeSearcher<R> searcher) {
        final IntLongMap map = searcher.map;
        final int[] key = branch.key();
        final Node<R>[] value = branch.value();
        final int size = key.length;
        final int depth = searcher.depth;
        int index;
        while ((index = this.index++) < size) {
            if (map.containsKey(key[index])) {
                R result = value[index].get(searcher, this);
                if (result != null || searcher.depth != depth) return result;
            }
        }
        searcher.depth--;
        return null;
    }

    /**
     * Visit every match of this frame by walking the input indices, delivering each to
     * {@code action}. Mirrors {@link #searchByInput} save for reporting every hit (and
     * frame-aware short-circuit when a {@link Node} reports traversal interruption).
     */
    void forEachByInput(RecipeSearcher<R> searcher, Consumer<? super R> action) {
        final int[] ints = searcher.ints;
        final int size = ints.length;
        final Branch<R> branch = this.branch;
        if (multi) {
            // multi-word path
            final long[] arr = skipArr;
            while (index < size) {
                int w = index >>> 6;
                if (w >= arr.length || (arr[w] & (1L << (index & 0x3F))) == 0) {
                    Node<R> node = branch.get(ints[index]);
                    if (node != null) {
                        if (node.forEach(searcher, this, action)) {
                            index++;
                            return;
                        }
                    }
                }
                index++;
            }
        } else {
            // single-word fast path: all representable indices are < 64
            final long word = skipWord;
            while (index < size) {
                if ((word & (1L << index)) == 0) {
                    Node<R> node = branch.get(ints[index]);
                    if (node != null) {
                        if (node.forEach(searcher, this, action)) {
                            index++;
                            return;
                        }
                    }
                }
                index++;
            }
        }
        searcher.depth--;
    }

    void forEachByBranch(RecipeSearcher<R> searcher, Consumer<? super R> action) {
        final IntLongMap map = searcher.map;
        final int[] key = branch.key();
        final Node<R>[] value = branch.value();
        final int size = key.length;
        int index;
        while ((index = this.index++) < size) {
            if (map.containsKey(key[index])) {
                if (value[index].forEach(searcher, this, action)) return;
            }
        }
        searcher.depth--;
    }

}
