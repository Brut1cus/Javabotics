package javabotics;

/**
 * Seeded 2D gradient (Perlin) noise. Gradients are picked by hashing the lattice
 * coordinates with the seed instead of a 256-entry permutation table, so the noise
 * never repeats and the world is genuinely infinite.
 */
final class PerlinNoise {
    private final long seed;

    PerlinNoise(long seed) {
        this.seed = mix(seed ^ 0x5DEECE66DL);
    }

    /** Single octave of noise, roughly in [-1, 1]. */
    double noise(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        double u = fade(xf);
        double v = fade(yf);

        double n00 = grad(hash(xi, yi), xf, yf);
        double n10 = grad(hash(xi + 1, yi), xf - 1, yf);
        double n01 = grad(hash(xi, yi + 1), xf, yf - 1);
        double n11 = grad(hash(xi + 1, yi + 1), xf - 1, yf - 1);

        double x0 = lerp(u, n00, n10);
        double x1 = lerp(u, n01, n11);
        return lerp(v, x0, x1);
    }

    /** Fractal Brownian motion: several octaves summed and normalised to roughly [-1, 1]. */
    double fbm(double x, double y, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * freq + i * 31.7, y * freq - i * 17.3);
            norm += amp;
            amp *= 0.5;
            freq *= 2;
        }
        return sum / norm;
    }

    private int hash(int x, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        return (int) mix(h);
    }

    static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    private static double grad(int hash, double x, double y) {
        return switch (hash & 7) {
            case 0 -> x + y;
            case 1 -> -x + y;
            case 2 -> x - y;
            case 3 -> -x - y;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> y;
            default -> -y;
        };
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
