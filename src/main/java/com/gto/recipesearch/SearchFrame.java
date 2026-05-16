package com.gto.recipesearch;

import java.util.function.Consumer;

public final class SearchFrame<R> {

    boolean branchProbe;
    int index;
    BitContainer skip;

    private Branch<R> branch;

    void push(Branch<R> branch, int size, SearchFrame<R> prev) {
        this.branchProbe = size > branch.size();
        this.index = 0;
        this.skip = prev.branchProbe ? prev.skip : prev.skip.add(prev.index);
        this.branch = branch;
    }

    void push(Branch<R> branch, int size, int expectedDepth) {
        this.branchProbe = size > branch.size();
        this.index = 0;
        this.skip = expectedDepth > 64 ? BitContainer.MULTI_LONG : BitContainer.LONG;
        this.branch = branch;
    }

    R searchByInput(RecipeSearcher<R> searcher) {
        final int[] ints = searcher.ints;
        final int size = ints.length;
        final BitContainer skip = this.skip;
        final Branch<R> branch = this.branch;
        final int depth = searcher.depth;
        while (index < size) {
            if (skip.notContains(index)) {
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

    void forEachByInput(RecipeSearcher<R> searcher, Consumer<? super R> action) {
        final int[] ints = searcher.ints;
        final int size = ints.length;
        final BitContainer skip = this.skip;
        final Branch<R> branch = this.branch;
        while (index < size) {
            if (skip.notContains(index)) {
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
