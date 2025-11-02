package io;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Automated, non-interactive benchmark runner comparing the three
 * compression strategies: CROSSING, NO_CROSSING, OVERFLOW.
 *
 * Produces a compact, printf-formatted table per scenario with:
 * - median compression time (5 runs)
 * - median decompression time (5 runs)
 * - average get(i) time over 1,000,000 random accesses
 * - final compressed size (bytes)
 */
public final class AutomatedBenchmark {

    public static void main(String[] args) {
        runAllScenarios();
    }

    // =============================================================
    // Scenarios
    // =============================================================

    /**
     * Define and run the requested scenarios.
     */
    public static void runAllScenarios() {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("         AUTOMATED COMPRESSION BENCHMARK       ");
        System.out.println("==============================================\n");

        // Scenario 1: Uniform data, small k (k=9, 32 not divisible by 9)
        {
            final int n = 1_000_000;
            final int maxValue = 511; // k=9 bits
            int[] data = genUniform(n, maxValue);
            runScenario("Uniformes (k=9, 32%9!=0)", data);
        }

        // Scenario 2: Uniform data, perfect k (k=8, 32 divisible by 8)
        {
            final int n = 1_000_000;
            final int maxValue = 255; // k=8 bits
            int[] data = genUniform(n, maxValue);
            runScenario("Uniformes (k=8, parfait)", data);
        }

        // Scenario 3: Outliers (overflow-friendly)
        {
            final int n = 1_000_000;
            final int baseMax = 63;           // ~6 bits for 99% of data
            final int everyK = 100;           // 1% outliers
            final int outlierMax = 1_000_000; // ~20 bits for outliers
            int[] data = genWithOutliers(n, baseMax, everyK, outlierMax);
            runScenario("Outliers (1%, k~6/20)", data);
        }

        System.out.println();
        System.out.println("==============================================");
        System.out.println("                 BENCHMARK FINI               ");
        System.out.println("==============================================");
    }

    /**
     * Run a single scenario across all compression strategies and print a table.
     */
    public static void runScenario(String name, int[] data) {
        final int warmups = 3;
        final int runs = 5;
        final int queries = 1_000_000;

        System.out.printf("%n-- Scénario: %s --%n", name);
        System.out.printf("%-12s | %-12s | %-12s | %-14s | %-12s%n",
                "Stratégie", "Comp", "Decomp", "Get (ns/op)", "Taille");
        System.out.println("--------------------------------------------------------------------------------");

        for (CompressionType type : new CompressionType[]{
                CompressionType.CROSSING,
                CompressionType.NO_CROSSING,
                CompressionType.OVERFLOW
        }) {
            IntCompressor compressor = CompressorFactory.create(type);

            // Warmup: a few passes to let JIT optimize
            for (int i = 0; i < warmups; i++) {
                int[] compressedWarm = compressor.compress(data);
                int n = Headers.n(compressedWarm);
                int[] recoveredWarm = new int[n];
                compressor.decompress(compressedWarm, recoveredWarm);
                // couple of random gets
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int j = 0; j < 5_000; j++) {
                    compressor.get(compressedWarm, rnd.nextInt(n));
                }
            }

            // Measure compression (median of 5)
            long[] compTimes = new long[runs];
            int[] compressed = null; // keep last result for subsequent stages
            for (int i = 0; i < runs; i++) {
                long t0 = System.nanoTime();
                compressed = compressor.compress(data);
                long t1 = System.nanoTime();
                compTimes[i] = (t1 - t0);
            }
            long compMed = median(compTimes);

            // Measure decompression (median of 5)
            int n = Headers.n(compressed);
            int[] recovered = new int[n];
            long[] decompTimes = new long[runs];
            for (int i = 0; i < runs; i++) {
                long t0 = System.nanoTime();
                compressor.decompress(compressed, recovered);
                long t1 = System.nanoTime();
                decompTimes[i] = (t1 - t0);
            }
            long decompMed = median(decompTimes);

            // Measure random get(i) average time (1,000,000 accesses)
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            long t0 = System.nanoTime();
            long checksum = 0;
            for (int i = 0; i < queries; i++) {
                int idx = rnd.nextInt(n);
                checksum += compressor.get(compressed, idx);
            }
            long t1 = System.nanoTime();
            double getAvgNs = (double) (t1 - t0) / queries;

            // Final compressed size (bytes)
            long sizeBytes = (long) compressed.length * 4L;

            // Print row
            System.out.printf(
                    Locale.ROOT,
                    "%-12s | %-12s | %-12s | %14.2f | %12s%n",
                    typeToLabel(type),
                    prettyNs(compMed),
                    prettyNs(decompMed),
                    getAvgNs,
                    String.format(Locale.ROOT, "%,d B", sizeBytes)
            );

            // small usage of checksum to avoid dead code elimination
            if (checksum == 42) {
                System.out.print("");
            }
        }
    }

    private static String typeToLabel(CompressionType t) {
        switch (t) {
            case CROSSING: return "Overlap";
            case NO_CROSSING: return "NoOverlap";
            case OVERFLOW: return "Overflow";
            default: return t.name();
        }
    }

    // =============================================================
    // Helpers copied from Main.java (adapted)
    // =============================================================

    private static int[] genUniform(int n, int maxValue) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] data = new int[n];
        for (int i = 0; i < n; i++) {
            data[i] = rnd.nextInt(maxValue + 1);
        }
        return data;
    }

    private static int[] genWithOutliers(int n, int baseMax, int everyK, int outlierMax) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] data = new int[n];
        for (int i = 0; i < n; i++) {
            if (everyK > 0 && i > 0 && (i % everyK == 0)) {
                data[i] = rnd.nextInt(outlierMax + 1);
            } else {
                data[i] = rnd.nextInt(baseMax + 1);
            }
        }
        return data;
    }

    private static long median(long[] arr) {
        long[] copy = Arrays.copyOf(arr, arr.length);
        Arrays.sort(copy);
        int mid = copy.length / 2;
        return (copy.length % 2 == 0) ? (copy[mid - 1] + copy[mid]) / 2 : copy[mid];
    }

    private static String prettyNs(long ns) {
        if (ns < 1_000)
            return ns + " ns";
        if (ns < 1_000_000)
            return String.format(Locale.ROOT, "%.3f us", ns / 1_000.0);
        if (ns < 1_000_000_000)
            return String.format(Locale.ROOT, "%.3f ms", ns / 1_000_000.0);
        return String.format(Locale.ROOT, "%.3f s", ns / 1_000_000_000.0);
    }
}

/* ANALYSE DES RÉSULTATS ATTENDUS

Ce benchmark met en évidence les forces et faiblesses des trois stratégies :

1) Uniformes (k=9, 32%9!=0)
   - NoOverlap : simple et accès get rapides (un seul mot), mais gaspille des bits
     de padding car 32 n'est pas multiple de 9 → taille plus grande.
   - Overlap (Crossing) : packe sans padding, meilleure compaction → taille plus petite,
     mais get peut être un peu plus complexe si une valeur chevauche 2 mots.
   - Overflow : peu d'intérêt ici (pas d'outliers), taille similaire à Overlap/NoOverlap
     selon l'implémentation; timings proches.

2) Uniformes (k=8, parfait)
   - NoOverlap : cas idéal, 4 valeurs par mot, zéro padding → très bonne taille et
     get très rapide (un seul mot).
   - Overlap : similaire en taille (pas de besoin de chevauchement), coûts comparables.
   - Overflow : surcoût d'en-tête et structure non nécessaire → taille/timing un peu moins bons.

3) Outliers (1%, base k~6, outliers ~20 bits)
   - Overflow : conçu pour ce cas; petites valeurs en ligne, outliers déportés dans
     une zone dédiée → excellente taille et souvent de bons temps.
   - Overlap : packe tous les bits indistinctement → la présence d'outliers augmente
     la largeur effective, taille finale plus grande que Overflow.
   - NoOverlap : combine padding + large k effectif si homogénéisé → risque de taille
     la plus élevée; get reste simple/rapide.

En résumé :
- NoOverlap brille quand 32 est multiple de k et pour les accès aléatoires.
- Overlap tend à mieux compacter quand 32%k!=0 (moins de padding).
- Overflow excelle quand une petite fraction d'outliers gonfle la distribution.
*/
