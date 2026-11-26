package javabotics;

/**
 * Biome definitions. Each biome is a set of terrain-shaping parameters; terrain between
 * biomes is produced by blending these parameters so there are no seams.
 *
 * <p>Biomes are ordered along the biome-noise axis: neighbours in this list are the ones
 * that blend into each other when the biome noise value sits between them.
 */
enum Biome {
    //            base  ampLow ampHigh rough  cave
    PLAINS(       0,     6,     2,     0.10, 0.06),
    HILLS(        6,    18,     5,     0.25, 0.08),
    CRAGS(        4,    14,     6,     0.90, 0.10),
    MOUNTAINS(   20,    48,    10,     0.35, 0.07),
    SKY_ISLES(    2,    10,     4,     1.60, 0.09);

    /** Average surface height. */
    final double base;
    /** Amplitude of broad, rolling height variation. */
    final double ampLow;
    /** Amplitude of short, bumpy height variation. */
    final double ampHigh;
    /** Strength of 2D density noise: creates overhangs, arches and floating islands. */
    final double roughness;
    /** Width of the worm-cave band underground (0 = no caves). */
    final double caveWidth;

    Biome(double base, double ampLow, double ampHigh, double roughness, double caveWidth) {
        this.base = base;
        this.ampLow = ampLow;
        this.ampHigh = ampHigh;
        this.roughness = roughness;
        this.caveWidth = caveWidth;
    }

    private static final Biome[] VALUES = values();

    /** Biome whose anchor is nearest to a biome-noise value in [0, 1]. */
    static Biome nearest(double v) {
        int i = (int) Math.floor(clamp01(v) * VALUES.length);
        return VALUES[Math.min(i, VALUES.length - 1)];
    }

    /**
     * Terrain parameters for a biome-noise value in [0, 1]. Each biome owns an equal slice
     * of the range; the parameters are flat in the middle of a slice and smoothly blended
     * near the edges, so a continuous noise value yields continuous terrain.
     */
    static void params(double v, double[] out) {
        int n = VALUES.length;
        double pos = clamp01(v) * n - 0.5;     // anchor k sits at pos == k
        int k = (int) Math.floor(pos);
        double t;
        Biome a;
        Biome b;
        if (k < 0) {
            a = b = VALUES[0];
            t = 0;
        } else if (k >= n - 1) {
            a = b = VALUES[n - 1];
            t = 0;
        } else {
            a = VALUES[k];
            b = VALUES[k + 1];
            t = smoothstep(0.3, 0.7, pos - k);
        }
        out[0] = lerp(a.base, b.base, t);
        out[1] = lerp(a.ampLow, b.ampLow, t);
        out[2] = lerp(a.ampHigh, b.ampHigh, t);
        out[3] = lerp(a.roughness, b.roughness, t);
        out[4] = lerp(a.caveWidth, b.caveWidth, t);
    }

    static final int PARAM_COUNT = 5;

    String displayName() {
        String s = name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double smoothstep(double e0, double e1, double x) {
        double t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
