package io;

public class BitPackingNoCrossing extends BitPackingBase {

    // ============================= Compression =============================
    @Override
    public int[] compress(int[] src) {
        if (src == null) throw new IllegalArgumentException("Source array is null");
        final int n = src.length;
        
        // Validation: pas de valeurs négatives
        for (int v : src) {
            if (v < 0) throw new IllegalArgumentException("Negative value not supported: " + v);
        }
        
        final int k = computeKAuto(src);
        final int elementsPerWord = elementsPerWord(k);
        final int wordsNeeded = wordsNeeded(n, elementsPerWord);
        final int dataBits = wordsNeeded * 32;
        
        int[] out = allocWithHeader(Headers.HEADER_WORDS, dataBits);
        Headers.write(out, n, CompressionType.NO_CROSSING, k, k, 0);
        
        final int baseWord = baseWord();
        for (int i = 0; i < n; i++) {
            int wordIndex = wordIndexFor(i, elementsPerWord);
            int bitOffset = bitOffsetFor(i, elementsPerWord, k);
            BitIO.writeBitsInWordLSB(out, baseWord + wordIndex, bitOffset, k, src[i]);
        }
        return out;
    }

    // ============================= Décompression =============================
    @Override
    public void decompress(int[] compressed, int[] dst) {
        if (compressed == null) throw new IllegalArgumentException("Compressed is null");
        if (dst == null) throw new IllegalArgumentException("Destination is null");
        
        final int n = Headers.n(compressed);
        final int k = Headers.k(compressed);
        if (dst.length < n) throw new IllegalArgumentException("Destination too small: " + dst.length + " < " + n);
        
        final int elementsPerWord = elementsPerWord(k);
        final int baseWord = baseWord();
        
        for (int i = 0; i < n; i++) {
            int wordIndex = wordIndexFor(i, elementsPerWord);
            int bitOffset = bitOffsetFor(i, elementsPerWord, k);
            dst[i] = BitIO.readBitsInWordLSB(compressed, baseWord + wordIndex, bitOffset, k);
        }
    }

    // ============================= Accès aléatoire =============================
    @Override
    public int get(int[] compressed, int index) {
        if (compressed == null) throw new IllegalArgumentException("Compressed is null");
        final int n = Headers.n(compressed);
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        
        final int k = Headers.k(compressed);
        final int elementsPerWord = elementsPerWord(k);
        final int wordIndex = wordIndexFor(index, elementsPerWord);
        final int bitOffset = bitOffsetFor(index, elementsPerWord, k);
        
        return BitIO.readBitsInWordLSB(compressed, baseWord() + wordIndex, bitOffset, k);
    }

    // ============================= Helpers internes =============================
    
    /**
     * Calcule le nombre d'éléments de k bits qui peuvent tenir dans un mot de 32 bits
     * sans chevauchement.
     * @param k Nombre de bits par élément (1-32)
     * @return floor(32/k), minimum 1
     */
    private static int elementsPerWord(int k) {
        if (k <= 0 || k > 32) {
            throw new IllegalArgumentException("k must be in range [1, 32], got: " + k);
        }
        int elemPerWord = 32 / k;
        return (elemPerWord <= 0) ? 1 : elemPerWord;
    }
    
    /**
     * Calcule le nombre de mots de 32 bits nécessaires pour stocker n éléments.
     * @param n Nombre d'éléments à stocker
     * @param elementsPerWord Nombre d'éléments par mot
     * @return Nombre de mots nécessaires (arrondi supérieur)
     */
    private static int wordsNeeded(int n, int elementsPerWord) {
        return (n + elementsPerWord - 1) / elementsPerWord;
    }
    
    /**
     * Position du premier mot de données après le header.
     * @return Index du premier mot de données
     */
    private static int baseWord() {
        return Headers.HEADER_WORDS;
    }
    
    /**
     * Calcule l'index du mot contenant l'élément i.
     * @param i Index de l'élément
     * @param elementsPerWord Nombre d'éléments par mot
     * @return Index relatif du mot (à ajouter à baseWord)
     */
    private static int wordIndexFor(int i, int elementsPerWord) {
        return i / elementsPerWord;
    }
    
    /**
     * Calcule l'offset en bits dans le mot pour l'élément i.
     * @param i Index de l'élément
     * @param elementsPerWord Nombre d'éléments par mot
     * @param k Nombre de bits par élément
     * @return Offset en bits depuis le LSB du mot
     */
    private static int bitOffsetFor(int i, int elementsPerWord, int k) {
        return (i % elementsPerWord) * k;
    }
}
