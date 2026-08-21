package com.gto.recipesearch;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A node of the recipe trie. Nodes come in five shapes ({@link RecipeLeaf},
 * {@link RecipeListLeaf}, {@link BranchOnly}, {@link RecipeBranch}, {@link RecipeListBranch})
 * that encode whether the node also continues into a sub-branch and how many recipes it
 * holds directly, instead of using flags or boxed collections.
 *
 * <p>{@link #createLeaf} and {@link #createBranch} are the shape transitions applied
 * while inserting: a leaf recipe becomes {@link RecipeLeaf}; adding another recipe to the
 * same node merges the lists into {@link RecipeListLeaf}; discovering a child branch
 * wraps the recipe into {@link RecipeBranch}/{@link RecipeListBranch} (or plain
 * {@link BranchOnly} when the node only branches). Branch optimizations are queued as
 * {@link Runnable}s and applied after all insertions.
 */
public interface Node<T> {

    @SuppressWarnings("unchecked")
    static <T> Node<T> createLeaf(List<Runnable> branchBuilder, Node<T> node, final T recipe) {
        if (node instanceof RecipeBranch<T> rb) {
            T[] arr = (T[]) new Object[2];
            arr[0] = rb.recipe;
            arr[1] = recipe;
            return new RecipeListBranch<>(branchBuilder, rb.branch, arr);
        } else if (node instanceof RecipeListBranch<T> rlb) {
            T[] arr = (T[]) new Object[rlb.recipes.length + 1];
            System.arraycopy(rlb.recipes, 0, arr, 0, rlb.recipes.length);
            arr[rlb.recipes.length] = recipe;
            return new RecipeListBranch<>(branchBuilder, rlb.branch, arr);
        } else if (node instanceof RecipeLeaf<T> r) {
            T[] arr = (T[]) new Object[2];
            arr[0] = r.recipe;
            arr[1] = recipe;
            return new RecipeListLeaf<>(arr);
        } else if (node instanceof RecipeListLeaf<T> rl) {
            T[] arr = (T[]) new Object[rl.recipes.length + 1];
            System.arraycopy(rl.recipes, 0, arr, 0, rl.recipes.length);
            arr[rl.recipes.length] = recipe;
            return new RecipeListLeaf<>(arr);
        } else if (node instanceof BranchOnly<T> b) {
            return new RecipeBranch<>(branchBuilder, b.branch, recipe);
        } else {
            return new RecipeLeaf<>(recipe);
        }
    }

    static <T> Node<T> createBranch(List<Runnable> branchBuilder, Node<T> node) {
        if (node instanceof BranchOnly || node instanceof RecipeBranch || node instanceof RecipeListBranch) {
            return node;
        } else if (node instanceof RecipeLeaf<T> r) {
            return new RecipeBranch<>(branchBuilder, Branch.create(), r.recipe);
        } else if (node instanceof RecipeListLeaf<T> rl) {
            return new RecipeListBranch<>(branchBuilder, Branch.create(), rl.recipes);
        } else {
            return new BranchOnly<>(branchBuilder);
        }
    }

    default T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
        return null;
    }

    /**
     * @return whether to interrupt the traversal of this frame
     */
    default boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
        return false;
    }

    interface BranchNode<T> extends Node<T> {

        Branch<T> branch();

        @Override
        default T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            int depth = ++context.depth;
            if (depth == context.maxDepth) context.expansion();
            context.frames[depth].push(branch(), context.inputKeys.length - depth, frame);
            return null;
        }

        @Override
        default boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            int depth = ++context.depth;
            if (depth == context.maxDepth) context.expansion();
            context.frames[depth].push(branch(), context.inputKeys.length - depth, frame);
            return true;
        }
    }

    /**
     * Node holding a single recipe with no child branch.
     */
    class RecipeLeaf<T> implements Node<T> {

        final T recipe;

        RecipeLeaf(final T recipe) {
            this.recipe = recipe;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            if (context.predicate.test(recipe)) return recipe;
            return null;
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            if (context.predicate.test(recipe)) action.accept(recipe);
            return false;
        }
    }

    /**
     * Node holding multiple recipes (sharing the same ingredient prefix) with no child
     * branch. Recipes are drained one at a time via {@link #get}, keeping a cursor in
     * {@code context.recipeCursor}.
     */
    class RecipeListLeaf<T> implements Node<T> {

        final T[] recipes;
        private final int length;

        RecipeListLeaf(final T[] recipes) {
            this.recipes = recipes;
            this.length = recipes.length;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            final Predicate<T> predicate = context.predicate;
            int index;
            while ((index = context.recipeCursor++) < length) {
                T recipe = recipes[index];
                if (predicate.test(recipe)) {
                    if (index < length - 1) context.drainingNode = this;
                    return recipe;
                }
            }
            context.recipeCursor = 0;
            context.drainingNode = null;
            return null;
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            final Predicate<T> predicate = context.predicate;
            for (T recipe : recipes) {
                if (predicate.test(recipe)) action.accept(recipe);
            }
            return false;
        }

    }

    /**
     * Node that only descends into a child branch; holds no recipe of its own.
     */
    final class BranchOnly<T> implements BranchNode<T> {

        private Branch<T> branch;

        private BranchOnly(List<Runnable> branchBuilder) {
            this.branch = Branch.create();
            branchBuilder.add(() -> this.branch = this.branch.optimize());
        }

        @Override
        public Branch<T> branch() {
            return branch;
        }
    }

    /**
     * Node holding a single recipe and continuing into a child branch.
     */
    final class RecipeBranch<T> extends RecipeLeaf<T> implements BranchNode<T> {

        private Branch<T> branch;

        private RecipeBranch(List<Runnable> branchBuilder, Branch<T> branch, T recipe) {
            super(recipe);
            this.branch = branch;
            branchBuilder.add(() -> this.branch = this.branch.optimize());
        }

        @Override
        public Branch<T> branch() {
            return branch;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            BranchNode.super.get(context, frame);
            if (context.predicate.test(recipe)) return recipe;
            return null;
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            BranchNode.super.forEach(context, frame, action);
            if (context.predicate.test(recipe)) action.accept(recipe);
            return true;
        }
    }

    /**
     * Node holding multiple recipes and continuing into a child branch.
     */
    final class RecipeListBranch<T> extends RecipeListLeaf<T> implements BranchNode<T> {

        private Branch<T> branch;

        private RecipeListBranch(List<Runnable> branchBuilder, Branch<T> branch, T[] recipes) {
            super(recipes);
            this.branch = branch;
            branchBuilder.add(() -> this.branch = this.branch.optimize());
        }

        @Override
        public Branch<T> branch() {
            return branch;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            if (frame != null) BranchNode.super.get(context, frame);
            return super.get(context, frame);
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            BranchNode.super.forEach(context, frame, action);
            final Predicate<T> predicate = context.predicate;
            for (T recipe : recipes) {
                if (predicate.test(recipe)) action.accept(recipe);
            }
            return true;
        }
    }
}
