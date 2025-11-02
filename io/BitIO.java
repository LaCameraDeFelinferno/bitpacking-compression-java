package io;

public final class BitIO {
    private BitIO() {}

    // Read bitLen bits starting at absolute bitPos (LSB within word). Returns unsigned int value.
    public static int readBitsLSB(int[] words, int bitPos, int bitLen) {
        if (bitLen <= 0) return 0;
        int wordIndex = bitPos / 32;
        int offset = bitPos % 32;
        int first = Math.min(32 - offset, bitLen);
        int rest = bitLen - first;

        int maskFirst = (first >= 32) ? -1 : ((1 << first) - 1);
        int value = (words[wordIndex] >>> offset) & maskFirst;
        if (rest > 0) {
            int maskRest = (rest >= 32) ? -1 : ((1 << rest) - 1);
            value |= (words[wordIndex + 1] & maskRest) << first;
        }
        return value;
    }

    public static void writeBitsLSB(int[] words, int bitPos, int bitLen, int value) {
        if (bitLen <= 0) return;
        int wordIndex = bitPos / 32;
        int offset = bitPos % 32;
        int first = Math.min(32 - offset, bitLen);
        int rest = bitLen - first;

        int maskFirst = (first >= 32) ? -1 : ((1 << first) - 1);
        // Clear destination bits then OR
        words[wordIndex] &= ~(maskFirst << offset);
        words[wordIndex] |= (value & maskFirst) << offset;

        if (rest > 0) {
            int maskRest = (rest >= 32) ? -1 : ((1 << rest) - 1);
            words[wordIndex + 1] &= ~maskRest;
            words[wordIndex + 1] |= (value >>> first) & maskRest;
        }
    }

    // In-word read: reads bitLen bits from words[wordIndex] starting at bitOffset (LSB within word)
    public static int readBitsInWordLSB(int[] words, int wordIndex, int bitOffset, int bitLen) {
        if (bitLen <= 0) return 0;
        int mask = (bitLen >= 32) ? -1 : ((1 << bitLen) - 1);
        return (words[wordIndex] >>> bitOffset) & mask;
    }

    public static void writeBitsInWordLSB(int[] words, int wordIndex, int bitOffset, int bitLen, int value) {
        if (bitLen <= 0) return;
        int mask = (bitLen >= 32) ? -1 : ((1 << bitLen) - 1);
        words[wordIndex] &= ~(mask << bitOffset);
        words[wordIndex] |= (value & mask) << bitOffset;
    }
}
