package com.gto.recipesearch;

import java.util.Arrays;

public interface BitContainer {

    BitContainer LONG = new LongImmutable(0) {
        @Override
        public boolean notContains(int index) {
            return true;
        }
    };

    BitContainer MULTI_LONG = new MultiLongImmutable(new long[0]) {
        @Override
        public boolean notContains(int index) {
            return true;
        }
    };

    default boolean contains(int index) {
        return !notContains(index);
    }

    boolean notContains(int index);

    BitContainer add(int index);

    class LongImmutable implements BitContainer {

        private final long word;

        private LongImmutable(long word) {
            this.word = word;
        }

        @Override
        public boolean notContains(int index) {
            return (word & (1L << index)) == 0;
        }

        @Override
        public BitContainer add(int index) {
            return new LongImmutable(word | (1L << index));
        }
    }

    class MultiLongImmutable implements BitContainer {

        private final long[] words;

        private MultiLongImmutable(long[] words) {
            this.words = words;
        }

        @Override
        public boolean notContains(int index) {
            int wordIndex = index >>> 6;
            if (wordIndex >= words.length) return true;
            int bitIndex = index & 0x3F;
            return (words[wordIndex] & (1L << bitIndex)) == 0;
        }

        @Override
        public BitContainer add(int index) {
            int wordIndex = index >>> 6;
            long[] newWords;
            int requiredWords = wordIndex + 1;
            if (requiredWords > words.length) {
                newWords = Arrays.copyOf(words, requiredWords);
            } else {
                newWords = words.clone();
            }
            int bitIndex = index & 0x3F;
            newWords[wordIndex] |= (1L << bitIndex);
            return new MultiLongImmutable(newWords);
        }
    }
}
