package com.gto.recipesearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchTest {

    private static Branch.HashBranch<String> branchWith(int... keys) {
        Branch.HashBranch<String> branch = new Branch.HashBranch<>();
        for (int key : keys) {
            branch.put(key, new Node.RecipeLeaf<>(String.valueOf(key)));
        }
        return branch;
    }

    @Test
    void hashBranchLookup() {
        Branch<String> branch = branchWith(1, 2);
        assertEquals("1", leafValue(branch.get(1)));
        assertEquals("2", leafValue(branch.get(2)));
        assertNull(branch.get(3));
        assertEquals(2, branch.size());
    }

    private static String leafValue(Node<String> node) {
        return ((Node.RecipeLeaf<String>) node).recipe;
    }

    @Test
    void keyAndValueArraysAlign() {
        Branch.HashBranch<String> branch = branchWith(1, 2, 3);
        int[] keys = branch.key();
        Node<String>[] values = branch.value();
        assertEquals(3, keys.length);
        for (int i = 0; i < keys.length; i++) {
            assertNotNull(branch.get(keys[i]));
            assertSame(values[i], branch.get(keys[i]));
        }
    }

    @Test
    void smallBranchOptimizesToLinearScan() {
        Branch<String> branch = branchWith(1, 2, 3);
        Branch<String> optimized = branch.optimize();
        assertNotNull(optimized);
        assertEquals(3, optimized.size());
        assertEquals("1", leafValue(optimized.get(1)));
        assertEquals("3", leafValue(optimized.get(3)));
        assertNull(optimized.get(4));
    }

    @Test
    void largeBranchStaysHashTable() {
        Branch<String> branch = branchWith(1, 2, 3, 4, 5);
        assertSame(branch, branch.optimize());
    }

    @Test
    void emptyBranchOptimizesToItself() {
        Branch<String> branch = Branch.create();
        assertSame(branch, branch.optimize());
    }
}
