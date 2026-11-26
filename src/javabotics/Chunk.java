package javabotics;

/**
 * A 16x16 square of blocks with two layers. World y grows upward.
 * <ul>
 *   <li><b>Foreground</b>: the editable blocks the player and creatures collide with.</li>
 *   <li><b>Background</b>: a purely visual copy of the terrain as originally generated. It is
 *       never edited and has no collision; it shows through wherever foreground is air.</li>
 * </ul>
 */
final class Chunk {
    static final int SIZE = 16;
    static final int SHIFT = 4;
    static final int MASK = SIZE - 1;

    static final byte AIR = Block.AIR;

    final int cx;
    final int cy;
    private final byte[] blocks = new byte[SIZE * SIZE];
    private final byte[] background = new byte[SIZE * SIZE];
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

    byte getBackground(int lx, int ly) {
        return background[(ly << SHIFT) | lx];
    }

    void setBackground(int lx, int ly, byte b) {
        background[(ly << SHIFT) | lx] = b;
    }

    static long key(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }
}
