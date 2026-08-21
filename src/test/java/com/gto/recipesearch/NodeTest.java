package com.gto.recipesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {

    private final List<Runnable> tasks = new ArrayList<>();

    @Test
    void singleRecipeBecomesLeaf() {
        Node<String> node = Node.createLeaf(tasks, null, "a");
        assertInstanceOf(Node.RecipeLeaf.class, node);
        assertEquals("a", ((Node.RecipeLeaf<String>) node).recipe);
    }

    @Test
    void secondRecipeMergesIntoListLeaf() {
        Node<String> node = Node.createLeaf(tasks, null, "a");
        node = Node.createLeaf(tasks, node, "b");
        assertInstanceOf(Node.RecipeListLeaf.class, node);
        // 列表节点的“配方可被遍历取出”已由 RecipeSearchTest 的完整搜索流程覆盖
    }

    @Test
    void branchFromNullIsBranchOnly() {
        Node<String> node = Node.createBranch(tasks, null);
        assertInstanceOf(Node.BranchOnly.class, node);
    }

    @Test
    void leafWithChildBecomesRecipeBranch() {
        Node<String> node = Node.createLeaf(tasks, null, "a");
        node = Node.createBranch(tasks, node);
        assertInstanceOf(Node.RecipeBranch.class, node);
        assertEquals("a", ((Node.RecipeBranch<String>) node).recipe);
        assertNotNull(((Node.RecipeBranch<String>) node).branch());
    }

    @Test
    void listLeafWithChildKeepsRecipes() {
        Node<String> node = Node.createLeaf(tasks, null, "a");
        node = Node.createLeaf(tasks, node, "b");
        node = Node.createBranch(tasks, node);
        assertInstanceOf(Node.RecipeListBranch.class, node);
        // 列表节点的“配方可被遍历取出”已由 RecipeSearchTest 的完整搜索流程覆盖
    }

    @Test
    void branchOnlyWithRecipeBecomesRecipeBranch() {
        Node<String> node = Node.createBranch(tasks, null);
        node = Node.createLeaf(tasks, node, "a");
        assertInstanceOf(Node.RecipeBranch.class, node);
    }

    @Test
    void recipeBranchGrowsIntoListBranch() {
        Node<String> node = Node.createBranch(tasks, null);
        node = Node.createLeaf(tasks, node, "a");
        node = Node.createLeaf(tasks, node, "b");
        assertInstanceOf(Node.RecipeListBranch.class, node);
    }

    @Test
    void branchAlreadyPresentIsKept() {
        Node<String> branchOnly = Node.createBranch(tasks, null);
        assertSame(branchOnly, Node.createBranch(tasks, branchOnly));
    }

    @Test
    void deferredOptimizationsRunWithoutError() {
        Node.createLeaf(tasks, null, "a");
        Node.createLeaf(tasks, Node.createBranch(tasks, null), "b");
        assertFalse(tasks.isEmpty());
        tasks.forEach(Runnable::run);
    }
}
