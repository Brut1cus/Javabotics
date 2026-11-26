package javabotics;

/**
 * Base class for everything that moves and collides: the player and creatures.
 *
 * <p>Position (x, y) is the bottom-left corner of the bounding box, in blocks; world y grows
 * upward. Each move loads only the tiles under the box's swept path into a tiny
 * {@link CollisionWindow} and resolves one axis at a time against it, so a step costs the
 * same no matter how big the world is, and fast bodies cannot tunnel through blocks.
 */
abstract class Entity {
    static final double GRAVITY = 34;
    static final double MAX_FALL = 45;
    private static final double EPS = 1e-7;

    final double width;
    final double height;
    /** Relative weight when two entities push each other apart. */
    final double mass;

    double x;
    double y;
    double prevX;
    double prevY;
    double vx;
    double vy;
    boolean onGround;
    /** Set by the last {@link #move} if horizontal movement was stopped by a block. */
    boolean hitWall;
    boolean removed;

    private final CollisionWindow window = new CollisionWindow();

    Entity(double x, double y, double width, double height, double mass) {
        this.x = prevX = x;
        this.y = prevY = y;
        this.width = width;
        this.height = height;
        this.mass = mass;
    }

    /** Advances one fixed step: AI / controls, then {@link #move}. */
    abstract void update(World world, EntityList entities, double dt);

    double centerX() {
        return x + width / 2;
    }

    double centerY() {
        return y + height / 2;
    }

    boolean overlaps(Entity o) {
        return x < o.x + o.width && x + width > o.x && y < o.y + o.height && y + height > o.y;
    }

    boolean overlapsTile(int tx, int ty) {
        return tx < x + width && tx + 1 > x && ty < y + height && ty + 1 > y;
    }

    // ------------------------------------------------------------------ world collision

    /** Moves by (dx, dy) with world collision. Sets {@link #onGround} / {@link #hitWall}. */
    void move(World world, double dx, double dy) {
        int minX = (int) Math.floor(Math.min(x, x + dx) + EPS);
        int maxX = (int) Math.floor(Math.max(x, x + dx) + width - EPS);
        int minY = (int) Math.floor(Math.min(y, y + dy) + EPS);
        int maxY = (int) Math.floor(Math.max(y, y + dy) + height - EPS);
        window.load(world, minX, minY, maxX, maxY);

        hitWall = false;
        moveX(dx);
        onGround = false;
        moveY(dy);
    }

    private void moveX(double dx) {
        if (dx == 0) return;
        int rowMin = (int) Math.floor(y + EPS);
        int rowMax = (int) Math.floor(y + height - EPS);
        if (dx > 0) {
            int first = (int) Math.floor(x + width - EPS) + 1;
            int last = (int) Math.floor(x + dx + width - EPS);
            for (int c = first; c <= last; c++) {
                if (columnBlocked(c, rowMin, rowMax)) {
                    x = c - width;
                    vx = 0;
                    hitWall = true;
                    return;
                }
            }
        } else {
            int first = (int) Math.floor(x + EPS) - 1;
            int last = (int) Math.floor(x + dx + EPS);
            for (int c = first; c >= last; c--) {
                if (columnBlocked(c, rowMin, rowMax)) {
                    x = c + 1;
                    vx = 0;
                    hitWall = true;
                    return;
                }
            }
        }
        x += dx;
    }

    private void moveY(double dy) {
        if (dy == 0) return;
        int colMin = (int) Math.floor(x + EPS);
        int colMax = (int) Math.floor(x + width - EPS);
        if (dy > 0) {
            int first = (int) Math.floor(y + height - EPS) + 1;
            int last = (int) Math.floor(y + dy + height - EPS);
            for (int r = first; r <= last; r++) {
                if (rowBlocked(r, colMin, colMax)) {
                    y = r - height;
                    vy = 0;
                    return;
                }
            }
        } else {
            int first = (int) Math.floor(y + EPS) - 1;
            int last = (int) Math.floor(y + dy + EPS);
            for (int r = first; r >= last; r--) {
                if (rowBlocked(r, colMin, colMax)) {
                    y = r + 1;
                    vy = 0;
                    onGround = true;
                    return;
                }
            }
        }
        y += dy;
    }

    private boolean columnBlocked(int c, int rowMin, int rowMax) {
        for (int r = rowMin; r <= rowMax; r++) if (window.isSolid(c, r)) return true;
        return false;
    }

    private boolean rowBlocked(int r, int colMin, int colMax) {
        for (int c = colMin; c <= colMax; c++) if (window.isSolid(c, r)) return true;
        return false;
    }

    /** True if the body is overlapping any solid tile (e.g. a block was placed into it). */
    boolean insideBlock(World world) {
        for (int tx = (int) Math.floor(x + EPS); tx <= (int) Math.floor(x + width - EPS); tx++) {
            for (int ty = (int) Math.floor(y + EPS); ty <= (int) Math.floor(y + height - EPS); ty++) {
                if (world.isSolid(tx, ty)) return true;
            }
        }
        return false;
    }

    CollisionWindow window() {
        return window;
    }

    static double approach(double v, double target, double step) {
        if (v < target) return Math.min(v + step, target);
        return Math.max(v - step, target);
    }
}
