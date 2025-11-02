package io;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class SmokeTest {
    public static void main(String[] args) {
        int n = 1000;
        int max = 255;
        int[] data = new int[n];
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) data[i] = rnd.nextInt(max + 1);

        for (CompressionType type : CompressionType.values()) {
            System.out.println("Testing " + type);
            IntCompressor c = CompressorFactory.create(type);
            int[] compressed = c.compress(data);
            int[] out = new int[Headers.n(compressed)];
            c.decompress(compressed, out);
            boolean ok = Arrays.equals(data, out);
            System.out.println("  decompress OK: " + ok + ", compressed ints=" + compressed.length);
            // spot check a few gets
            boolean getsOk = true;
            for (int i = 0; i < 10; i++) {
                int idx = rnd.nextInt(n);
                int a = data[idx];
                int b = c.get(compressed, idx);
                if (a != b) { getsOk = false; break; }
            }
            System.out.println("  sample get() OK: " + getsOk);
        }
        System.out.println("Smoke test done.");
    }
}
