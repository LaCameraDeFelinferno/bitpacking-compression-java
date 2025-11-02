package io;

public class BitPackingCrossing extends BitPackingBase {

    @Override
    public int[] compress(int[] src) {
        if (src == null) throw new IllegalArgumentException("Source array is null");
        final int n = src.length;
        final int k = computeKAuto(src);

        int dataBits = n * k;
        int[] out = allocWithHeader(Headers.HEADER_WORDS, dataBits);

    // write header: no overflow for crossing
    Headers.write(out, n, CompressionType.CROSSING, k, k, 0);

        int baseBit = Headers.HEADER_WORDS * 32;
        for (int i = 0; i < n; i++) {
            int bitPos = baseBit + i * k;
            BitIO.writeBitsLSB(out, bitPos, k, src[i]);
        }
        return out;
    }

    @Override
    public void decompress(int[] compressed, int[] dst) {
        if (compressed == null) throw new IllegalArgumentException("Compressed is null");
        if (dst == null) throw new IllegalArgumentException("Destination is null");
        int n = Headers.n(compressed);
        int k = Headers.k(compressed);
        if (dst.length < n) throw new IllegalArgumentException("Destination too small: " + dst.length + " < " + n);

        int baseBit = Headers.HEADER_WORDS * 32;
        for (int i = 0; i < n; i++) {
            int bitPos = baseBit + i * k;
            dst[i] = BitIO.readBitsLSB(compressed, bitPos, k);
        }
    }

    @Override
    public int get(int[] compressed, int index) {
        if (compressed == null) throw new IllegalArgumentException("Compressed is null");
        int n = Headers.n(compressed);
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        int k = Headers.k(compressed);
        int bitPos = Headers.HEADER_WORDS * 32 + index * k;
        return BitIO.readBitsLSB(compressed, bitPos, k);
    }
}
