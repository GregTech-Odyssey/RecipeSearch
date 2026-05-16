package com.gto.recipesearch;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public interface Branch<R> {

    static <R> Branch<R> create() {
        return new HashBranch<>();
    }

    Node<R> get(int i);

    int size();

    int[] key();

    Node<R>[] value();

    default Branch<R> optimize() {
        return this;
    }

    final class HashBranch<R> extends Int2ObjectOpenHashMap<Node<R>> implements Branch<R> {

        private int[] k;
        private Node<R>[] v;

        @Override
        @SuppressWarnings("unchecked")
        public Node<R> get(int k) {
            final int[] key = this.key;
            int curr;
            int pos;
            if ((curr = key[pos = HashCommon.mix(k) & mask]) != 0) {
                do if (curr == k) {
                    Object[] value = super.value;
                    return (Node<R>) value[pos];
                }
                while ((curr = key[pos = (pos + 1) & this.mask]) != 0);
            }
            return null;
        }

        @Override
        public int[] key() {
            if (k == null) {
                k = keySet().toIntArray();
                keys = null;
            }
            return k;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Node<R>[] value() {
            if (v == null) {
                v = values().toArray(new Node[0]);
                values = null;
            }
            return v;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Branch<R> optimize() {
            if (size == 0) return null;
            if (size < 5) return new ArrayBranch<>(keySet().toIntArray(), values().toArray(new Node[0]));
            return this;
        }

        private static final class ArrayBranch<R> implements Branch<R> {

            private final int[] key;
            private final Node<R>[] value;

            private ArrayBranch(int[] key, Node<R>[] value) {
                this.key = key;
                this.value = value;
            }

            @Override
            public Node<R> get(int i) {
                int size = key.length;
                for (int j = 0; j < size; j++) {
                    if (i == key[j]) {
                        return value[j];
                    }
                }
                return null;
            }

            @Override
            public int size() {
                return key.length;
            }

            @Override
            public int[] key() {
                return key;
            }

            @Override
            public Node<R>[] value() {
                return value;
            }
        }
    }


}
