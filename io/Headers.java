package io;

public final class Headers {
    public static final int HEADER_WORDS = 5;
    // Magic number used to detect a valid compressed blob. Chosen arbitrarily.
    public static final int MAGIC = 0x1A2B3C4D;

    private Headers() {}

    /**
     * Write header words into out[0..4]
     * Layout (words):
     * 0: MAGIC
     * 1: n (number of elements)
     * 2: mode (CompressionType.ordinal())
     * 3: k (bits requested, e.g. k used for main values)
     * 4: packed: (bitsPerElement << 16) | bitsPerOverflow
     *    - bitsPerElement: width (in bits) of each main entry (including flag/index bits)
     *    - bitsPerOverflow: width (in bits) of overflow full values (0 if none)
     */
    public static void write(int[] out, int n, CompressionType mode, int k, int bitsPerElement, int bitsPerOverflow) {
        if (out == null || out.length < HEADER_WORDS) throw new IllegalArgumentException("Output array too small for header");
        out[0] = MAGIC;
        out[1] = n;
        out[2] = mode == null ? -1 : mode.ordinal();
        out[3] = k;
        int packed = (bitsPerElement << 16) | (bitsPerOverflow & 0xFFFF);
        out[4] = packed;
    }

    public static void checkMagic(int[] in) {
        if (in == null || in.length < HEADER_WORDS) throw new IllegalArgumentException("Input array too small or null");
        if (in[0] != MAGIC) throw new IllegalArgumentException("Invalid magic in header: expected " + Integer.toHexString(MAGIC));
    }

    public static int n(int[] in) {
        checkMagic(in);
        return in[1];
    }

    public static CompressionType mode(int[] in) {
        checkMagic(in);
        int ordinal = in[2];
        if (ordinal < 0 || ordinal >= CompressionType.values().length) throw new IllegalArgumentException("Invalid compression mode in header: " + ordinal);
        return CompressionType.values()[ordinal];
    }

    public static int k(int[] in) {
        checkMagic(in);
        return in[3];
    }

    public static int bitsPerElement(int[] in) {
        checkMagic(in);
        return (in[4] >>> 16) & 0xFFFF;
    }

    public static int bitsPerOverflow(int[] in) {
        checkMagic(in);
        return in[4] & 0xFFFF;
    }

    /**
     * Compute the word index where overflow area starts based on header values.
     */
    public static int overflowOffset(int[] in) {
        checkMagic(in);
        int n = n(in);
        int bpe = bitsPerElement(in);
        int baseBit = HEADER_WORDS * 32;
        int overflowStartBit = baseBit + n * bpe;
        return overflowStartBit / 32;
    }
}
