package io;

/**
 * Bit-packing avec zone d'overflow.
 * Chaque entrée principale encode un bit de drapeau (MSB) indiquant si la valeur
 * est stockée dans la zone principale (flag=0) ou en overflow (flag=1). Les bits
 * restants de l'entrée principale contiennent soit la valeur (tronquée à k bits),
 * soit l'index de la valeur dans la zone d'overflow.
 *
 * API publique conservée à l'identique: compress, decompress, get, findBestK.
 */
public class BitPackingOverflow extends BitPackingBase {

	/** Résultat du choix optimal de k et des largeurs en bits. */
	public record BestKResult(int bestK, int bestTotalCost, int bestbitsParElement, int maxBits) {}

	// ============================= Compression =============================
	@Override
	public int[] compress(int[] src) {
		if (src == null) throw new IllegalArgumentException("Source is null");
		final int n = src.length;
		// Validation basique des données (non négatives)
		for (int v : src) {
			if (v < 0) throw new IllegalArgumentException("Negative value not supported: " + v);
		}

		BestKResult r = findBestK(src, n);
		final int k = r.bestK();
		final int bitsParElement = r.bestbitsParElement();
		final int bitsParOverflow = r.maxBits();
		final int totalBits = r.bestTotalCost();

		int[] out = allocWithHeader(Headers.HEADER_WORDS, totalBits);

		// Header: bitsPerElement et bitsParOverflow packés
		Headers.write(out, n, CompressionType.OVERFLOW, k, bitsParElement, bitsParOverflow);

		final int baseBit = baseBit();
		final int flagMask = flagMask(bitsParElement);
		final int idxMask = indexMask(bitsParElement);
		final int maxMain = maxMainValue(k);

		int overflowIndex = 0;
		for (int i = 0; i < n; i++) {
			int v = src[i];
			if (v > maxMain) {
				// Écrire la valeur dans la zone overflow
				int overflowBitPos = overflowBitPos(baseBit, n, bitsParElement, overflowIndex, bitsParOverflow);
				BitIO.writeBitsLSB(out, overflowBitPos, bitsParOverflow, v);

				// Entrée principale: flag=1 et index overflow dans les (bitsParElement-1) LSB
				int entry = flagMask | (overflowIndex & idxMask);
				int bitPos = mainBitPos(baseBit, i, bitsParElement);
				BitIO.writeBitsLSB(out, bitPos, bitsParElement, entry);
				overflowIndex++;
			} else {
				// Entrée principale: flag=0 et valeur v tronquée dans les (bitsParElement-1) LSB
				int entry = v & idxMask;
				int bitPos = mainBitPos(baseBit, i, bitsParElement);
				BitIO.writeBitsLSB(out, bitPos, bitsParElement, entry);
			}
		}
		return out;
	}

	// ============================= Décompression =============================
	@Override
	public void decompress(int[] compressed, int[] dst) {
		if (compressed == null) throw new IllegalArgumentException("Compressed is null");
		if (dst == null) throw new IllegalArgumentException("Destination is null");

		final int n = Headers.n(compressed);
		final int bitsParElement = Headers.bitsPerElement(compressed);
		final int bitsParOverflow = Headers.bitsPerOverflow(compressed);
		if (dst.length < n) throw new IllegalArgumentException("Destination too small: " + dst.length + " < " + n);

		final int baseBit = baseBit();
		final int flagBitShift = bitsParElement - 1;
		final int idxMask = indexMask(bitsParElement);

		for (int i = 0; i < n; i++) {
			int bitPos = mainBitPos(baseBit, i, bitsParElement);
			int entry = BitIO.readBitsLSB(compressed, bitPos, bitsParElement);
			int flag = (entry >>> flagBitShift) & 1;
			if (flag == 1) {
				int overflowIndex = entry & idxMask;
				int overflowBitPos = overflowBitPos(baseBit, n, bitsParElement, overflowIndex, bitsParOverflow);
				dst[i] = BitIO.readBitsLSB(compressed, overflowBitPos, bitsParOverflow);
			} else {
				dst[i] = entry & idxMask;
			}
		}
	}

	// ============================= Accès aléatoire =============================
	@Override
	public int get(int[] compressed, int index) {
		if (compressed == null) throw new IllegalArgumentException("Compressed is null");
		int n = Headers.n(compressed);
		if (index < 0 || index >= n) throw new IndexOutOfBoundsException("Index out of bounds: " + index);

		final int bitsParElement = Headers.bitsPerElement(compressed);
		final int bitsParOverflow = Headers.bitsPerOverflow(compressed);
		final int baseBit = baseBit();
		final int flagBitShift = bitsParElement - 1;
		final int idxMask = indexMask(bitsParElement);

		int bitPos = mainBitPos(baseBit, index, bitsParElement);
		int entry = BitIO.readBitsLSB(compressed, bitPos, bitsParElement);
		int flag = (entry >>> flagBitShift) & 1;
		if (flag == 1) {
			int overflowIndex = entry & idxMask;
			int overflowBitPos = overflowBitPos(baseBit, n, bitsParElement, overflowIndex, bitsParOverflow);
			return BitIO.readBitsLSB(compressed, overflowBitPos, bitsParOverflow);
		} else {
			return entry & idxMask;
		}
	}

	// ============================= Sélection du meilleur k =============================
	/**
	 * Calcule le meilleur k (bits pour la zone principale) afin de minimiser le coût total.
	 * Conserve exactement la logique précédente (mêmes résultats).
	 */
	public static BestKResult findBestK(int[] array, int n) {
		int bestK = 1;
		int bestbitsParElement = 1;
		int bestTotalCost = Integer.MAX_VALUE;
		int maxBits = 0;

		for (int v : array) {
			if (v < 0) throw new IllegalArgumentException("Negative value not supported: " + v);
			int bits = bitsRequired(v);
			if (bits > maxBits) maxBits = bits;
		}

		for (int k = 1; k <= maxBits; k++) {
			int nOverflow = 0;
			for (int v : array) {
				int bits = bitsRequired(v);
				if (bits > k) nOverflow++;
			}
			int indexBits = (nOverflow > 0) ? bitsRequired(nOverflow) : 0;
			int bitsParElement = 1 + Math.max(k, indexBits);

			long overflowCost = (long) nOverflow * maxBits;
			long totalCost = (long) n * bitsParElement + overflowCost;
			if (totalCost > Integer.MAX_VALUE) continue; // on laisse l'alloc échouer proprement sinon
			if (totalCost < bestTotalCost) {
				bestTotalCost = (int) totalCost;
				bestK = k;
				bestbitsParElement = bitsParElement;
			}
		}
		return new BestKResult(bestK, bestTotalCost, bestbitsParElement, maxBits);
	}

	// ============================= Helpers internes =============================
	private static int baseBit() {
		return Headers.HEADER_WORDS * 32;
	}

	private static int mainBitPos(int baseBit, int index, int bitsParElement) {
		return baseBit + index * bitsParElement;
	}

	private static int overflowBitPos(int baseBit, int n, int bitsParElement, int overflowIndex, int bitsParOverflow) {
		return baseBit + n * bitsParElement + overflowIndex * bitsParOverflow;
	}

	private static int flagMask(int bitsParElement) {
		return 1 << (bitsParElement - 1);
	}

	private static int indexMask(int bitsParElement) {
		return (bitsParElement <= 1) ? 0 : ((1 << (bitsParElement - 1)) - 1);
	}

	private static int maxMainValue(int k) {
		return (k >= 31) ? Integer.MAX_VALUE : ((1 << k) - 1);
	}

	private static int bitsRequired(int v) {
		return (v == 0) ? 1 : (32 - Integer.numberOfLeadingZeros(v));
	}
}
