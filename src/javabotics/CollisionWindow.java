package javabotics;

/**
 * A tiny, reusable snapshot of the blocks around a moving body.
 *
 * <p>Each physics step the player loads only the tiles covered by its swept bounding box
 * (its box at the start of the step unioned with its box at the end) into this window -
 * typically 2x3 to 3x4 tiles. Collision resolution then runs entirely against this flat
 * array, never touching the chunk map, so the cost per step is constant and tiny no matter
 * how large the loaded world is.
 */
final class CollisionWindow {
    private boolean[] solid = new boolean[32];
    private int originX;
    private int originY;
    private int width;
    private int height;
    private World world;

    /** Loads every tile in the inclusive tile range [minX..maxX] x [minY..maxY]. */
    void load(World world, int minX, int minY, int maxX, int maxY) {
        this.world = world;
        originX = minX;
        originY = minY;
        width = maxX - minX + 1;
        height = maxY - minY + 1;
        int n = width * height;
        if (solid.length < n) solid = new boolean[Integer.highestOneBit(n) << 1];
        int i = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                solid[i++] = world.isSolid(x, y);
            }
        }
    }

    boolean isSolid(int x, int y) {
        int lx = x - originX;
        int ly = y - originY;
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) {
            return world.isSolid(x, y); // outside the window: should not happen, but stay correct
        }
        return solid[ly * width + lx];
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int originX() {
        return originX;
    }

    int originY() {
        return originY;
    }
}
