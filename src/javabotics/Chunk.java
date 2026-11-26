package javabotics;

/**
 * A 16x16 square of blocks with two layers, the black layer and the blue layer (see
 * {@link World}). World y grows upward.
 */
final class Chunk {
    static final int SIZE = 16;
    static final int SHIFT = 4;
    static final int MASK = SIZE - 1;

    static final byte AIR = Block.AIR;

    final int cx;
    final int cy;
    private final byte[][] layers = {new byte[SIZE * SIZE], new byte[SIZE * SIZE]};
    /** True once the player has edited this chunk: it is then never unloaded, so edits persist. */
    boolean modified;

    Chunk(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    byte get(int layer, int lx, int ly) {
        return layers[layer][(ly << SHIFT) | lx];
    }

    void set(int layer, int lx, int ly, byte b) {
        layers[layer][(ly << SHIFT) | lx] = b;
    }

    static long key(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }
}
