package javabotics;

/**
 * Deterministic, infinite terrain built from two layers of 2D Perlin noise.
 *
 * <ol>
 *   <li><b>Biome layer.</b> The world is split horizontally into biome regions whose widths
 *       are 8-16 chunks (128-256 blocks). Region boundaries sit on a 12-chunk grid with a
 *       hashed jitter of +/-2 chunks, so every region width falls in [8, 16]. The biome
 *       noise is sampled in 2D at (region index, depth), so a region's character can also
 *       change with depth (e.g. calm plains on top, craggy caverns below). Parameters of
 *       neighbouring regions are blended across a 24-block band at each boundary.</li>
 *   <li><b>Terrain layer.</b> A 2D density field: a height-map term (surface height from
 *       noise) plus 2D noise scaled by the biome's roughness (overhangs, floating islands),
 *       with worm caves carved out of a second slice of the same noise.</li>
 * </ol>
 */
final class TerrainGenerator {
    static final int REGION_GRID_CHUNKS = 12;
    static final int REGION_JITTER_CHUNKS = 2;
    static final int BLEND_BLOCKS = 24;

    private final long seed;
    private final PerlinNoise biomeNoise;
    private final PerlinNoise terrainNoise;

    // scratch buffers (generation runs on the game thread only)
    private final double[] pa = new double[Biome.PARAM_COUNT];
    private final double[] pb = new double[Biome.PARAM_COUNT];

    TerrainGenerator(long seed) {
        this.seed = seed;
        this.biomeNoise = new PerlinNoise(seed * 31 + 1);
        this.terrainNoise = new PerlinNoise(seed * 31 + 2);
    }

    long seed() {
        return seed;
    }

    // ---------------------------------------------------------------- biome regions

    /** First chunk x of biome region {@code i}. */
    int regionStartChunk(int i) {
        long h = PerlinNoise.mix(seed ^ (i * 0xD1B54A32D192ED03L));
        int jitter = Math.floorMod(h, 2 * REGION_JITTER_CHUNKS + 1) - REGION_JITTER_CHUNKS;
        return i * REGION_GRID_CHUNKS + jitter;
    }

    /** Index of the biome region containing chunk column {@code cx}. */
    int regionOfChunk(int cx) {
        int i = Math.floorDiv(cx, REGION_GRID_CHUNKS);
        while (cx < regionStartChunk(i)) i--;
        while (cx >= regionStartChunk(i + 1)) i++;
        return i;
    }

    /** Biome-noise value in [0, 1] for a region at a given block height. */
    double biomeValue(int region, double y) {
        double n = biomeNoise.fbm(region * 0.618 + 0.31, y / 160.0 + 0.47, 2);
        return Biome.clamp01(0.5 + n * 1.1);
    }

    /** Surface biome at block column x (used for display). */
    Biome biomeAt(int x) {
        return Biome.nearest(biomeValue(regionOfChunk(Math.floorDiv(x, Chunk.SIZE)), 0));
    }

    // ---------------------------------------------------------------- generation

    void generate(Chunk chunk) {
        int x0 = chunk.cx * Chunk.SIZE;
        int y0 = chunk.cy * Chunk.SIZE;
        for (int lx = 0; lx < Chunk.SIZE; lx++) {
            int x = x0 + lx;

            // Which two regions influence this column, and how much.
            int cx = Math.floorDiv(x, Chunk.SIZE);
            int region = regionOfChunk(cx);
            int start = regionStartChunk(region) * Chunk.SIZE;
            int end = regionStartChunk(region + 1) * Chunk.SIZE;
            int other = region;
            double wOther = 0;
            if (x - start < BLEND_BLOCKS) {
                other = region - 1;
                wOther = 0.5 - 0.5 * Biome.smoothstep(0, 1, (x - start + 0.5) / BLEND_BLOCKS);
            } else if (end - x <= BLEND_BLOCKS) {
                other = region + 1;
                wOther = 0.5 - 0.5 * Biome.smoothstep(0, 1, (end - x - 0.5) / BLEND_BLOCKS);
            }

            // Surface height: biome parameters taken at the surface reference layer (y = 0).
            blendedParams(region, other, wOther, 0);
            double hLow = terrainNoise.fbm(x / 96.0, 1000.5, 3);
            double hHigh = terrainNoise.fbm(x / 24.0, 2000.5, 2);
            double surface = pa[0] + pa[1] * hLow + pa[2] * hHigh;

            for (int ly = 0; ly < Chunk.SIZE; ly++) {
                int y = y0 + ly;
                // Roughness / caves use the biome at this depth (2D biome field).
                blendedParams(region, other, wOther, y);
                double roughness = pa[3];
                double caveWidth = pa[4];

                double density = (surface - y) / 12.0
                        + roughness * terrainNoise.fbm(x / 28.0, y / 22.0, 3);
                boolean solid = density > 0;

                if (solid) {
                    double depth = surface - y;
                    if (depth > 3) {
                        double ramp = Biome.smoothstep(3, 24, depth);
                        double worm = Math.abs(terrainNoise.fbm(x / 48.0 + 500.5, y / 32.0 - 500.5, 2));
                        if (worm < caveWidth * ramp) solid = false;
                    }
                }
                byte b = solid ? Chunk.SOLID : Chunk.AIR;
                chunk.set(lx, ly, b);
                chunk.setBackground(lx, ly, b);
            }
        }
    }

    /** Fills {@link #pa} with region parameters blended toward {@code other} by {@code w}. */
    private void blendedParams(int region, int other, double w, double y) {
        Biome.params(biomeValue(region, y), pa);
        if (w > 0) {
            Biome.params(biomeValue(other, y), pb);
            for (int i = 0; i < Biome.PARAM_COUNT; i++) pa[i] = Biome.lerp(pa[i], pb[i], w);
        }
    }
}
