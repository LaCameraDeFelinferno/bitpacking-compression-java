package io;

public abstract class BitPackingBase implements IntCompressor {

    protected int computeKAuto(int[] src) {
        if (src == null) throw new IllegalArgumentException("Source cannot be null");
        int max = 0;
        for (int v : src) {
            if (v < 0) throw new IllegalArgumentException("Negative value not supported: " + v + 
                ". This may be caused by integer overflow when creating the input data.");
            if (v > max) max = v;
        }
        int bits = (max == 0) ? 1 : 32 - Integer.numberOfLeadingZeros(max);
        if (bits > 31) throw new IllegalArgumentException("Value needs more than 31 bits: " + max + 
            " requires " + bits + " bits. Maximum supported value is " + Integer.MAX_VALUE);
        return bits;
    }

    protected int[] allocWithHeader(int headerWords, int dataBits) {
        if (headerWords < 0) headerWords = 0;
        if (dataBits < 0) throw new IllegalArgumentException("dataBits cannot be negative: " + dataBits);
        
        // Utiliser long pour éviter les débordements
        long totalBitsLong = (long) headerWords * 32L + (long) dataBits;
        if (totalBitsLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Data too large: total bits would be " + totalBitsLong + 
                " which exceeds maximum supported size of " + Integer.MAX_VALUE + " bits");
        }
        
        long wordsLong = (totalBitsLong + 31L) / 32L;
        if (wordsLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Array too large: would need " + wordsLong + 
                " words which exceeds maximum array size");
        }
        
        int words = (int) wordsLong;
        return new int[words];
    }
}
