package io;

public interface IntCompressor {
    // Compress source array into a newly allocated int[] (including header)
    int[] compress(int[] src);

    // Decompress compressed into dst (dst must be large enough)
    void decompress(int[] compressed, int[] dst);

    // Read single element at index from compressed blob
    int get(int[] compressed, int index);
}
