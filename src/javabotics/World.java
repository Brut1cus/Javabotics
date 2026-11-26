package javabotics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Infinite chunked world with two block layers: the <b>black</b> layer and the <b>blue</b>
 * layer. Both start as a copy of the generated terrain. Exactly one layer is in <em>front</em>
 * at a time: it is drawn on top, it is the one the player edits, and it is the only one with
 * collision. The other layer sits behind as scenery. Chunks are generated lazily and unloaded
 * when far away.
 */
final class World {
    static final int BLACK = 0;
    static final int BLUE = 1;

    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final TerrainGenerator generator;
    private Chunk lastChunk; // one-entry cache: consecutive lookups usually hit the same chunk
    private int front = BLACK;

    World(long seed) {
        this.generator = new TerrainGenerator(seed);
    }

    TerrainGenerator generator() {
        return generator;
    }

    int frontLayer() {
        return front;
    }

    int backLayer() {
        return 1 - front;
    }

    void setFrontLayer(int layer) {
        front = layer;
    }

    byte getBlock(int layer, int x, int y) {
        int cx = x >> Chunk.SHIFT;
        int cy = y >> Chunk.SHIFT;
        Chunk c = lastChunk;
        if (c == null || c.cx != cx || c.cy != cy) {
            c = chunk(cx, cy);
            lastChunk = c;
        }
        return c.get(layer, x & Chunk.MASK, y & Chunk.MASK);
    }

    /** Block in the front layer. */
    byte getBlock(int x, int y) {
        return getBlock(front, x, y);
    }

    void setBlock(int layer, int x, int y, byte block) {
        Chunk c = chunk(x >> Chunk.SHIFT, y >> Chunk.SHIFT);
        c.set(layer, x & Chunk.MASK, y & Chunk.MASK, block);
        c.modified = true;
    }

    /** Collision: only the front layer is solid. */
    boolean isSolid(int x, int y) {
        return getBlock(front, x, y) != Block.AIR;
    }

    Chunk chunk(int cx, int cy) {
        long key = Chunk.key(cx, cy);
        Chunk c = chunks.get(key);
        if (c == null) {
            c = new Chunk(cx, cy);
            generator.generate(c);
            chunks.put(key, c);
        }
        return c;
    }

    /** Drops unmodified chunks further than {@code radius} chunks (Chebyshev distance) from a block position. */
    void unloadFarFrom(double x, double y, int radius) {
        int pcx = (int) Math.floor(x) >> Chunk.SHIFT;
        int pcy = (int) Math.floor(y) >> Chunk.SHIFT;
        Iterator<Chunk> it = chunks.values().iterator();
        while (it.hasNext()) {
            Chunk c = it.next();
            if (!c.modified && (Math.abs(c.cx - pcx) > radius || Math.abs(c.cy - pcy) > radius)) {
                if (c == lastChunk) lastChunk = null;
                it.remove();
            }
        }
    }

    int loadedChunks() {
        return chunks.size();
    }

    /** Highest solid block at column x, searching downward from {@code fromY}. */
    int surfaceY(int x, int fromY) {
        for (int y = fromY; y > fromY - 4096; y--) {
            if (isSolid(x, y)) return y;
        }
        return fromY - 4096;
    }
}
