package io;

public class BitPackingFactory {
    public static IntCompressor createBitPacking(String type) {
        if (type == null) throw new IllegalArgumentException("type null");
        switch(type) {
            case "Chevauchement":
                return CompressorFactory.create(CompressionType.CROSSING);
            case "Sans chevauchement":
                return CompressorFactory.create(CompressionType.NO_CROSSING);
            case "Debordement":
                return CompressorFactory.create(CompressionType.OVERFLOW);
            default:
                throw new IllegalArgumentException("Type inconnu: " + type);
        }
    }
}
