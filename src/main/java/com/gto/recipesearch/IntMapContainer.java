package com.gto.recipesearch;

public class IntMapContainer {

    final int[] key;
    long[] value;

    public IntMapContainer(int[] key) {
        this.key = key;
    }

    public boolean match(IntLongMap map) {
        final int[] k = key;
        final long[] v = value;
        for (int i = k.length - 1; i >= 0; i--) {
            if (map.get(k[i]) < v[i]) {
                return false;
            }
        }
        return true;
    }

}
