package javabotics;

/** A 16x16 square of blocks. World y grows upward. */
final class Chunk {
    static final int SIZE = 16;
    static final int SHIFT = 4;
    static final int MASK = SIZE - 1;

    static final byte AIR = 0;
    static final byte SOLID = 1;

    final int cx;
    final int cy;
    private final byte[] blocks = new byte[SIZE * SIZE];
    /** True once the player has edited this chunk: it is then never unloaded, so edits persist. */
    boolean modified;

    Chunk(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    byte get(int lx, int ly) {
        return blocks[(ly << SHIFT) | lx];
    }

    void set(int lx, int ly, byte b) {
        blocks[(ly << SHIFT) | lx] = b;
    }

    static long key(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }
}
