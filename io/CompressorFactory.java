package io;

public final class CompressorFactory {
    private CompressorFactory() {}

    public static IntCompressor create(CompressionType type) {
        if (type == null) throw new IllegalArgumentException("CompressionType cannot be null");
        switch (type) {
            case CROSSING:
                return new BitPackingCrossing();
            case NO_CROSSING:
                return new BitPackingNoCrossing();
            case OVERFLOW:
                return new BitPackingOverflow();
            default:
                throw new IllegalArgumentException("Unknown CompressionType: " + type);
        }
    }
}
