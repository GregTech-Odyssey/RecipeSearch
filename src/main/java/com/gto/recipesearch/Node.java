package com.gto.recipesearch;

import java.util.List;
import java.util.function.Consumer;

public interface Node<T> {

    @SuppressWarnings("unchecked")
    static <T> Node<T> recipe(List<Runnable> branchBuilder, Node<T> node, final T value) {
        if (node instanceof BR) {
            BR<T> br = (BR<T>) node;
            T[] arr = (T[]) new Object[2];
            arr[0] = br.r;
            arr[1] = value;
            return new BMR<>(branchBuilder, br.b, arr);
        } else if (node instanceof BMR) {
            BMR<T> bmr = (BMR<T>) node;
            T[] arr = (T[]) new Object[bmr.rs.length + 1];
            System.arraycopy(bmr.rs, 0, arr, 0, bmr.rs.length);
            arr[bmr.rs.length] = value;
            return new BMR<>(branchBuilder, bmr.b, arr);
        } else if (node instanceof R) {
            R<T> r = (R<T>) node;
            T[] arr = (T[]) new Object[2];
            arr[0] = r.r;
            arr[1] = value;
            return new MR<>(arr);
        } else if (node instanceof MR) {
            MR<T> mr = (MR<T>) node;
            T[] arr = (T[]) new Object[mr.rs.length + 1];
            System.arraycopy(mr.rs, 0, arr, 0, mr.rs.length);
            arr[mr.rs.length] = value;
            return new MR<>(arr);
        } else if (node instanceof B) {
            B<T> b = (B<T>) node;
            return new BR<>(branchBuilder, b.b, value);
        } else {
            return new R<>(value);
        }
    }

    static <T> Node<T> branch(List<Runnable> branchBuilder, Node<T> node) {
        if (node instanceof B || node instanceof BR || node instanceof BMR) {
            return node;
        } else if (node instanceof R) {
            R<T> r = (R<T>) node;
            return new BR<>(branchBuilder, Branch.create(), r.r);
        } else if (node instanceof MR) {
            MR<T> mr = (MR<T>) node;
            return new BMR<>(branchBuilder, Branch.create(), mr.rs);
        } else {
            return new B<>(branchBuilder);
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
            context.frames[depth].push(branch(), context.ints.length - depth, frame);
            return null;
        }

        @Override
        default boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            int depth = ++context.depth;
            if (depth == context.maxDepth) context.expansion();
            context.frames[depth].push(branch(), context.ints.length - depth, frame);
            return true;
        }
    }

    class R<T> implements Node<T> {

        final T r;

        R(final T r) {
            this.r = r;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            if (context.predicate.test(r)) return r;
            return null;
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            if (context.predicate.test(r)) action.accept(r);
            return false;
        }
    }

    class MR<T> implements Node<T> {

        final T[] rs;
        private final int length;

        MR(final T[] rs) {
            this.rs = rs;
            this.length = rs.length;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            int index;
            while ((index = context.count++) < length) {
                T r = rs[index];
                if (context.predicate.test(r)) {
                    if (index < length - 1) context.node = this;
                    return r;
                }
            }
            context.count = 0;
            context.node = null;
            return null;
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            for (T r : rs) {
                if (context.predicate.test(r)) action.accept(r);
            }
            return false;
        }

    }

    final class B<T> implements BranchNode<T> {

        private Branch<T> b;

        private B(List<Runnable> branchBuilder) {
            this.b = Branch.create();
            branchBuilder.add(() -> this.b = this.b.optimize());
        }

        @Override
        public Branch<T> branch() {
            return b;
        }
    }

    final class BR<T> extends R<T> implements BranchNode<T> {

        private Branch<T> b;

        private BR(List<Runnable> branchBuilder, Branch<T> b, T r) {
            super(r);
            this.b = b;
            branchBuilder.add(() -> this.b = this.b.optimize());
        }

        @Override
        public Branch<T> branch() {
            return b;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            BranchNode.super.get(context, frame);
            if (context.predicate.test(r)) return r;
            return null;
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            BranchNode.super.forEach(context, frame, action);
            if (context.predicate.test(r)) action.accept(r);
            return true;
        }
    }

    final class BMR<T> extends MR<T> implements BranchNode<T> {

        private Branch<T> b;

        private BMR(List<Runnable> branchBuilder, Branch<T> b, T[] rs) {
            super(rs);
            this.b = b;
            branchBuilder.add(() -> this.b = this.b.optimize());
        }

        @Override
        public Branch<T> branch() {
            return b;
        }

        @Override
        public T get(RecipeSearcher<T> context, SearchFrame<T> frame) {
            if (frame != null) BranchNode.super.get(context, frame);
            return super.get(context, frame);
        }

        @Override
        public boolean forEach(RecipeSearcher<T> context, SearchFrame<T> frame, Consumer<? super T> action) {
            BranchNode.super.forEach(context, frame, action);
            for (T r : rs) {
                if (context.predicate.test(r)) action.accept(r);
            }
            return true;
        }
    }
}
