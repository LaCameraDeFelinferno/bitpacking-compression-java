package io;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Point d'entrée principal pour le benchmark de compression bit-packing.
 * Structure le programme en phases distinctes : configuration, génération de données,
 * exécution des benchmarks et affichage des résultats.
 */
public class Main {
    private static final int DEFAULT_WARMUP = 3;
    private static final int DEFAULT_RUNS = 5;
    private static final int DEFAULT_MAX_VALUE_UNIFORM = 4095;
    private static final int DEFAULT_MAX_VALUE_OUTLIERS = 63;
    private static final int DEFAULT_OUTLIER_FREQUENCY = 1000;
    private static final int DEFAULT_OUTLIER_MAX = 1 << 20;

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            UI.printHeader();
            
            // Phase 1: Configuration de la compression
            CompressionConfig compressionConfig = UI.promptCompressionMode(sc);
            
            // Phase 2: Configuration des données
            DataConfig dataConfig = UI.promptDataConfiguration(sc);
            
            // Phase 3: Configuration du benchmark
            BenchmarkConfig benchConfig = UI.promptBenchmarkParameters(sc);
            
            // Phase 4: Génération des données
            int[] data = DataGenerator.generate(dataConfig);
            
            // Phase 5: Exécution des benchmarks
            IntCompressor compressor = CompressorFactory.create(compressionConfig.type);
            BenchmarkResults results = BenchmarkRunner.run(compressor, data, benchConfig);
            
            // Phase 6: Affichage des résultats
            UI.displayResults(compressionConfig, dataConfig, benchConfig, results);
        }
    }

    // ========================= CLASSES DE CONFIGURATION =========================
    
    /**
     * Configuration du mode de compression sélectionné.
     */
    static class CompressionConfig {
        final CompressionType type;
        final String displayName;
        
        CompressionConfig(CompressionType type, String displayName) {
            this.type = type;
            this.displayName = displayName;
        }
    }
    
    /**
     * Configuration des données à générer pour le benchmark.
     */
    static class DataConfig {
        final int size;
        final boolean isUniform;
        final int maxValue;
        final int outlierFrequency;
        final int outlierMax;
        
        DataConfig(int size, boolean isUniform, int maxValue, int outlierFrequency, int outlierMax) {
            this.size = size;
            this.isUniform = isUniform;
            this.maxValue = maxValue;
            this.outlierFrequency = outlierFrequency;
            this.outlierMax = outlierMax;
        }
        
        String getDescription() {
            if (isUniform) {
                return "Uniformes (max=" + maxValue + ")";
            } else {
                return "Outliers (max=" + maxValue + ", freq=" + outlierFrequency + ")";
            }
        }
    }
    
    /**
     * Configuration des paramètres du benchmark (warmup, runs).
     */
    static class BenchmarkConfig {
        final int warmups;
        final int runs;
        
        BenchmarkConfig(int warmups, int runs) {
            this.warmups = warmups;
            this.runs = runs;
        }
    }
    
    /**
     * Résultats d'exécution du benchmark.
     */
    static class BenchmarkResults {
        final long[] compressTimes;
        final long[] decompressTimes;
        final double nsPerGet;
        final boolean validationOk;
        final double compressionRatio; 
        final long originalSizeBytes;
        final long compressedSizeBytes;
        
        BenchmarkResults(long[] compressTimes, long[] decompressTimes, double nsPerGet, boolean validationOk, 
                        double compressionRatio, long originalSizeBytes, long compressedSizeBytes) {
            this.compressTimes = compressTimes;
            this.decompressTimes = decompressTimes;
            this.nsPerGet = nsPerGet;
            this.validationOk = validationOk;
            this.compressionRatio = compressionRatio;
            this.originalSizeBytes = originalSizeBytes;
            this.compressedSizeBytes = compressedSizeBytes;
        }
    }
    
    // ========================= GÉNÉRATEUR DE DONNÉES =========================
    
    /**
     * Génère les données de test selon la configuration spécifiée.
     */
    static class DataGenerator {
        static int[] generate(DataConfig config) {
            return config.isUniform 
                ? generateUniform(config.size, config.maxValue)
                : generateWithOutliers(config.size, config.maxValue, config.outlierFrequency, config.outlierMax);
        }
        
        private static int[] generateUniform(int n, int maxValue) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int[] data = new int[n];
            for (int i = 0; i < n; i++) {
                data[i] = rnd.nextInt(maxValue + 1);
            }
            return data;
        }
        
        private static int[] generateWithOutliers(int n, int baseMax, int everyK, int outlierMax) {
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
    }
    
    // ========================= EXÉCUTION DES BENCHMARKS =========================
    
    /**
     * Exécute les benchmarks de compression, décompression et accès aléatoires.
     */
    static class BenchmarkRunner {
        static BenchmarkResults run(IntCompressor compressor, int[] data, BenchmarkConfig config) {
            UI.println("\n┌─ ÉTAPE 4/4 : EXÉCUTION DES BENCHMARKS ───────────────┐");
            UI.println("│                                                      │");
            
            // Benchmark compression
            long[] compressTimes = benchmarkCompression(compressor, data, config);
            
            // Récupération du résultat compressé pour les tests suivants
            int[] compressed = compressor.compress(data);
            
            // Validation
            boolean validationOk = validateCompression(compressor, data, compressed);
            
            // Benchmark décompression
            long[] decompressTimes = benchmarkDecompression(compressor, compressed, config);
            
            // Benchmark accès aléatoires
            double nsPerGet = benchmarkRandomAccess(compressor, compressed, config);
            
            UI.println("│                                                      │");
            UI.println("└──────────────────────────────────────────────────────┘");
            
            // Calcul du taux de compression
            long originalSizeBytes = (long) data.length * 4L; // 4 bytes par int
            long compressedSizeBytes = (long) compressed.length * 4L; // 4 bytes par int
            double compressionRatio = (double) originalSizeBytes / compressedSizeBytes;
            
            return new BenchmarkResults(compressTimes, decompressTimes, nsPerGet, validationOk, 
                                       compressionRatio, originalSizeBytes, compressedSizeBytes);
        }
        
        private static long[] benchmarkCompression(IntCompressor compressor, int[] data, BenchmarkConfig config) {
            UI.println("│  ⏳ Warmup en cours (" + config.warmups + " itérations)...                 │");
            for (int i = 0; i < config.warmups; i++) {
                compressor.compress(data);
            }
            UI.println("│  ✓ Warmup terminé                                    │");
            UI.println("│  📊 Mesure compression (" + config.runs + " runs)...                    │");
            
            long[] times = new long[config.runs];
            for (int i = 0; i < config.runs; i++) {
                long t0 = System.nanoTime();
                compressor.compress(data);
                long t1 = System.nanoTime();
                times[i] = t1 - t0;
            }
            UI.println("│  ✓ Compression mesurée                               │");
            return times;
        }
        
        private static boolean validateCompression(IntCompressor compressor, int[] original, int[] compressed) {
            int nPacked = Headers.n(compressed);
            int[] recovered = new int[nPacked];
            compressor.decompress(compressed, recovered);
            return Arrays.equals(original, recovered);
        }
        
        private static long[] benchmarkDecompression(IntCompressor compressor, int[] compressed, BenchmarkConfig config) {
            UI.println("│  📊 Mesure décompression (" + config.runs + " runs)...                  │");
            
            int nPacked = Headers.n(compressed);
            int[] recovered = new int[nPacked];
            
            // Warmup
            for (int i = 0; i < config.warmups; i++) {
                compressor.decompress(compressed, recovered);
            }
            
            // Mesure
            long[] times = new long[config.runs];
            for (int i = 0; i < config.runs; i++) {
                long t0 = System.nanoTime();
                compressor.decompress(compressed, recovered);
                long t1 = System.nanoTime();
                times[i] = t1 - t0;
            }
            UI.println("│  ✓ Décompression mesurée                             │");
            return times;
        }
        
        private static double benchmarkRandomAccess(IntCompressor compressor, int[] compressed, BenchmarkConfig config) {
            int size = Headers.n(compressed);
            int queries = Math.min(1_000_000, Math.max(100_000, size));
            
            // Formater le nombre de queries avec un padding adapté
            String queriesStr = String.format("%,d", queries);
            UI.println("│  📊 Mesure get(i) aléatoires (" + queriesStr + " accès)...       │");
            
            // Warmup
            for (int i = 0; i < config.warmups; i++) {
                performRandomGets(compressor, compressed, size, 10_000);
            }
            
            // Mesure
            long t0 = System.nanoTime();
            performRandomGets(compressor, compressed, size, queries);
            long t1 = System.nanoTime();
            
            UI.println("│  ✓ Accès aléatoires mesurés                          │");
            return (double) (t1 - t0) / queries;
        }
        
        private static long performRandomGets(IntCompressor compressor, int[] compressed, int n, int queries) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            long acc = 0;
            for (int i = 0; i < queries; i++) {
                int idx = rnd.nextInt(n);
                acc += compressor.get(compressed, idx);
            }
            return acc; // Checksum pour éviter l'optimisation du compilateur
        }
    }
    
    // ========================= INTERFACE UTILISATEUR =========================
    
    /**
     * Gère toute l'interaction avec l'utilisateur et l'affichage.
     */
    static class UI {
        private static final int DISPLAY_WIDTH = 28;
        
        static void printHeader() {
            println("\n╔═══════════════════════════════════════════════════════╗");
            println("║          BIT PACKING - COMPRESSION BENCHMARK          ║");
            println("╚═══════════════════════════════════════════════════════╝\n");
        }
        
        static CompressionConfig promptCompressionMode(Scanner sc) {
            println("┌─ ÉTAPE 1/4 : MODE DE COMPRESSION ────────────────────┐");
            println("│                                                      │");
            println("│  [1] Overlap      - Chevauchement                    │");
            println("│  [2] NoOverlap    - Sans chevauchement               │");
            println("│  [3] Overflow     - Gestion débordement              │");
            println("│                                                      │");
            println("└──────────────────────────────────────────────────────┘");
            print("➤ Votre choix [1-3]: ");
            
            int choice = InputHelper.readInt(sc, 1, 3);
            CompressionType type;
            String displayName;
            
            switch (choice) {
                case 1:
                    type = CompressionType.CROSSING;
                    displayName = "Chevauchement";
                    break;
                case 2:
                    type = CompressionType.NO_CROSSING;
                    displayName = "Sans chevauchement";
                    break;
                case 3:
                    type = CompressionType.OVERFLOW;
                    displayName = "Debordement";
                    break;
                default:
                    throw new IllegalStateException("Choix invalide");
            }
            
            return new CompressionConfig(type, displayName);
        }
        
        static DataConfig promptDataConfiguration(Scanner sc) {
            println("\n┌─ ÉTAPE 2/4 : GÉNÉRATION DES DONNÉES ──────────────────┐");
            println("│                                                       │");
            println("│  [1] Uniforme    - Valeurs bornées [0, max]           │");
            println("│  [2] Outliers    - Valeurs normales + quelques pics   │");
            println("│                                                       │");
            println("└───────────────────────────────────────────────────────┘");
            print("➤ Votre choix [1-2]: ");
            
            int dataChoice = InputHelper.readInt(sc, 1, 2);
            boolean isUniform = (dataChoice == 1);
            
            print("\n  → Taille du tableau n (ex: 100000): ");
            int size = InputHelper.readInt(sc, 1, Integer.MAX_VALUE);
            
            if (isUniform) {
                int maxValue = InputHelper.readIntWithDefault(
                    sc, 
                    "  → Valeur max (ex: 4095 pour ~12 bits) [défaut=4095]: ",
                    DEFAULT_MAX_VALUE_UNIFORM
                );
                return new DataConfig(size, true, maxValue, 0, 0);
            } else {
                int maxValue = InputHelper.readIntWithDefault(
                    sc,
                    "  → Valeur max hors-outliers (ex: 63 pour ~6 bits) [défaut=63]: ",
                    DEFAULT_MAX_VALUE_OUTLIERS
                );
                int outlierFreq = InputHelper.readIntWithDefault(
                    sc,
                    "  → Fréquence des outliers (ex: 1000 = tous les 1000 éléments) [défaut=1000]: ",
                    DEFAULT_OUTLIER_FREQUENCY
                );
                int outlierMax = InputHelper.readIntWithDefault(
                    sc,
                    "  → Valeur max des outliers (ex: 1048576 pour ~20 bits) [défaut=1048576]: ",
                    DEFAULT_OUTLIER_MAX
                );
                return new DataConfig(size, false, maxValue, Math.max(2, outlierFreq), Math.max(maxValue + 1, outlierMax));
            }
        }
        
        static BenchmarkConfig promptBenchmarkParameters(Scanner sc) {
            println("\n┌─ ÉTAPE 3/4 : PARAMÈTRES DE BENCHMARK ────────────────┐");
            println("│                                                      │");
            print("│  → Nombre de warmups (échauffements) [défaut=" + DEFAULT_WARMUP + "]:");
            Integer warmups = InputHelper.tryParseInt(sc.nextLine().trim());
            if (warmups == null) warmups = DEFAULT_WARMUP;
            
            print("│  → Nombre de runs (mesures)         [défaut=" + DEFAULT_RUNS + "]:");
            Integer runs = InputHelper.tryParseInt(sc.nextLine().trim());
            if (runs == null) runs = DEFAULT_RUNS;
            
            println("│                                                      │");
            println("└──────────────────────────────────────────────────────┘");
            
            return new BenchmarkConfig(warmups, runs);
        }
        
        static void displayResults(CompressionConfig compressionConfig, DataConfig dataConfig, 
                                   BenchmarkConfig benchConfig, BenchmarkResults results) {
            println("\n╔═══════════════════════════════════════════════════════╗");
            println("║                    RÉSULTATS FINAUX                   ║");
            println("╚═══════════════════════════════════════════════════════╝");
            
            // Configuration
            println("\n┌─ CONFIGURATION ───────────────────────────────────────┐");
            println("│                                                       │");
            println("│  Mode compression    : " + padRight(compressionConfig.displayName, DISPLAY_WIDTH) + "   │");
            println("│  Taille du tableau   : " + padRight(String.format("%,d", dataConfig.size), DISPLAY_WIDTH) + "   │");
            println("│  Type de données     : " + padRight(dataConfig.getDescription(), DISPLAY_WIDTH) + "   │");
            println("│  Warmups / Runs      : " + padRight(benchConfig.warmups + " / " + benchConfig.runs, DISPLAY_WIDTH) + "   │");
            String correctness = results.validationOk ? "OK" : "ERREUR";
            println("│  Validation          : " + padRight(correctness, DISPLAY_WIDTH) + "   │");
            println("│                                                       │");
            println("└───────────────────────────────────────────────────────┘");
            
            // Performances
            println("\n┌─ PERFORMANCES (médiane) ──────────────────────────────┐");
            println("│                                                       │");
            println("│  Compression         : " + padRight(FormatHelper.prettyNs(FormatHelper.median(results.compressTimes)), DISPLAY_WIDTH) + "   │");
            println("│  Décompression       : " + padRight(FormatHelper.prettyNs(FormatHelper.median(results.decompressTimes)), DISPLAY_WIDTH) + "   │");
            println("│  Accès get(i)        : " + padRight(String.format(Locale.ROOT, "%.2f ns/accès", results.nsPerGet), DISPLAY_WIDTH) + "   │");
            println("│                                                       │");
            println("└───────────────────────────────────────────────────────┘");
            
            // Compression
            println("\n┌─ COMPRESSION ─────────────────────────────────────────┐");
            println("│                                                       │");
            println("│  Taille originale    : " + padRight(String.format(Locale.ROOT, "%,d octets", results.originalSizeBytes), DISPLAY_WIDTH) + "   │");
            println("│  Taille compressée   : " + padRight(String.format(Locale.ROOT, "%,d octets", results.compressedSizeBytes), DISPLAY_WIDTH) + "   │");
            println("│  Ratio de compression: " + padRight(String.format(Locale.ROOT, "%.3fx", results.compressionRatio), DISPLAY_WIDTH) + "   │");
            
            // Calcul intelligent de l'économie/surcoût d'espace
            if (results.compressionRatio >= 1.0) {
                double savings = (1.0 - 1.0/results.compressionRatio) * 100;
                println("│  Économie d'espace   : " + padRight(String.format(Locale.ROOT, "%.1f%%", savings), DISPLAY_WIDTH) + "   │");
            } else {
                double overhead = (1.0/results.compressionRatio - 1.0) * 100;
                println("│  Surcoût d'espace    : " + padRight(String.format(Locale.ROOT, "+%.1f%%", overhead), DISPLAY_WIDTH) + "   │");
            }
            
            println("│                                                       │");
            println("└───────────────────────────────────────────────────────┘");
            
            println("\n╔═══════════════════════════════════════════════════════╗");
            println("║             ✓ BENCHMARK TERMINÉ AVEC SUCCÈS           ║");
            println("╚═══════════════════════════════════════════════════════╝\n");
        }
        
        static void println(String s) {
            System.out.println(s);
        }
        
        static void print(String s) {
            System.out.print(s);
        }
        
        private static String padRight(String s, int length) {
            if (s.length() >= length) return s.substring(0, length);
            return s + " ".repeat(length - s.length());
        }
    }
    
    // ========================= HELPERS =========================
    
    /**
     * Utilitaires pour la lecture et validation des entrées utilisateur.
     */
    static class InputHelper {
        static int readInt(Scanner sc, int min, int max) {
            while (true) {
                String input = sc.nextLine().trim();
                try {
                    int value = Integer.parseInt(input);
                    if (value >= min && value <= max) {
                        return value;
                    }
                } catch (NumberFormatException ignored) {
                }
                UI.print("Entree invalide. Recommencez [" + min + "-" + max + "]: ");
            }
        }
        
        static int readIntWithDefault(Scanner sc, String prompt, int defaultValue) {
            UI.print(prompt);
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                long val = Long.parseLong(input);
                if (val > Integer.MAX_VALUE) {
                    UI.println("Valeur trop grande, limitee a " + Integer.MAX_VALUE);
                    return Integer.MAX_VALUE;
                } else if (val < 1) {
                    UI.println("Valeur trop petite, limitee a 1");
                    return 1;
                }
                return (int) val;
            } catch (NumberFormatException e) {
                UI.println("Entree invalide. Valeur par defaut utilisee : " + defaultValue);
                return defaultValue;
            }
        }
        
        static Integer tryParseInt(String s) {
            if (s == null || s.isEmpty()) return null;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
    
    /**
     * Utilitaires pour le formatage des résultats.
     */
    static class FormatHelper {
        static String prettyNs(long ns) {
            if (ns < 1_000)
                return ns + " ns";
            if (ns < 1_000_000)
                return String.format(Locale.ROOT, "%.3f us", ns / 1_000.0);
            if (ns < 1_000_000_000)
                return String.format(Locale.ROOT, "%.3f ms", ns / 1_000_000.0);
            return String.format(Locale.ROOT, "%.3f s", ns / 1_000_000_000.0);
        }
        
        static long median(long[] arr) {
            long[] copy = Arrays.copyOf(arr, arr.length);
            Arrays.sort(copy);
            int mid = copy.length / 2;
            return (copy.length % 2 == 0) ? (copy[mid - 1] + copy[mid]) / 2 : copy[mid];
        }
    }
}


