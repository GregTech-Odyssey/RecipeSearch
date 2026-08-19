package com.gto.recipesearch;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

@SuppressWarnings("unused")
public class IntLongMap extends Int2LongOpenHashMap {

    public static final IntLongMap EMPTY = new IntLongMap(0) {

        @Override
        public long get(final int k) {
            return 0;
        }

        public boolean containsKey(final int k) {
            return false;
        }

        @Override
        public void putAll(IntLongMap map) {
        }

        @Override
        public void setAll(IntLongMap map) {
        }

        @Override
        public void set(final int k, final long v) {
        }

        @Override
        public void copyTo(IntLongMap map) {
        }

        @Override
        public void add(final int k, final long incr) {
        }

        @Override
        public void copyToArray(int[] key, long[] value) {
        }

        @Override
        public int[] toIntArray() {
            return new int[0];
        }
    };

    public IntLongMap(int expected) {
        super(expected, 0.75F);
    }

    public IntLongMap() {
        super(16, 0.75F);
    }

    public IntLongMap(IntLongMap map) {
        super(map.size, 0.75F);
        putAll(map);
    }

    @Override
    public long addTo(final int k, final long incr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long put(final int k, final long v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long get(final int k) {
        final int[] key = this.key;
        int curr;
        int pos;
        if ((curr = key[pos = HashCommon.mix(k) & mask]) != 0) {
            do if (curr == k) {
                return value[pos];
            }
            while ((curr = key[pos = (pos + 1) & this.mask]) != 0);
        }
        return 0;
    }

    @Override
    public boolean containsKey(final int k) {
        final int[] key = this.key;
        int curr;
        int pos;
        if ((curr = key[pos = HashCommon.mix(k) & mask]) != 0) {
            do if (curr == k) {
                return true;
            }
            while ((curr = key[pos = (pos + 1) & this.mask]) != 0);
        }
        return false;
    }

    public void add(final int k, final long incr) {
        if (k == 0 || incr == 0) return;
        int pos;
        int curr;
        final int[] key = this.key;
        if ((curr = key[pos = HashCommon.mix(k) & this.mask]) != 0) {
            do if (curr == k) {
                long v = value[pos] + incr;
                if (v < 0) v = Long.MAX_VALUE;
                value[pos] = v;
                return;
            }
            while ((curr = key[pos = (pos + 1) & this.mask]) != 0);
        }
        key[pos] = k;
        value[pos] = incr;
        if (this.size++ >= this.maxFill) rehash(HashCommon.arraySize(this.size + 1, this.f));
    }

    /**
     * Set (overwrite) a key with the given value. If the key already exists its value is
     * replaced; otherwise the entry is inserted. Unlike {@link #add}, this does not combine
     * amounts, so distinct virtual items mapping to the same key will not be double-counted.
     */
    public void set(final int k, final long v) {
        if (k == 0) return;
        int pos;
        int curr;
        final int[] key = this.key;
        if ((curr = key[pos = HashCommon.mix(k) & this.mask]) != 0) {
            do {
                if (curr == k) {
                    value[pos] = v;
                    return;
                }
            }
            while ((curr = key[pos = (pos + 1) & this.mask]) != 0);
        }
        key[pos] = k;
        value[pos] = v;
        if (this.size++ >= this.maxFill) rehash(HashCommon.arraySize(this.size + 1, this.f));
    }

    public void putAll(IntLongMap map) {
        final int size = map.size;
        if (size == 0) return;
        final int[] key = map.key;
        final long[] value = map.value;
        int pos = map.n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                this.add(k, value[pos]);
                if (++i == size) break;
            }
        }
    }

    /**
     * Batch set: overwrite the entries of {@code map} into this map (merge with
     * set/overwrite semantics instead of accumulate). Entries already present are replaced.
     */
    public void setAll(IntLongMap map) {
        final int size = map.size;
        if (size == 0) return;
        final int[] key = map.key;
        final long[] value = map.value;
        int pos = map.n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                this.set(k, value[pos]);
                if (++i == size) break;
            }
        }
    }

    public void copyTo(IntLongMap map) {
        final int size = this.size;
        if (size == 0) return;
        final int[] key = this.key;
        final long[] value = this.value;
        int pos = this.n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                map.add(k, value[pos]);
                if (++i == size) break;
            }
        }
    }

    public void copyToArray(int[] ints, long[] longs) {
        final int size = this.size;
        if (size == 0) return;
        final int[] key = this.key;
        final long[] value = this.value;
        int pos = this.n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                ints[i] = k;
                longs[i] = value[pos];
                if (++i == size) break;
            }
        }
    }

    public int[] toIntArray() {
        final int size = this.size;
        final int[] key = this.key;
        int[] a = new int[size];
        int pos = this.n;
        int i = 0;
        while (pos-- != 0) {
            int k = key[pos];
            if (k != 0) {
                a[i] = k;
                if (++i == size) break;
            }
        }
        return a;
    }
}
