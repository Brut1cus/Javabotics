package javabotics;

/**
 * The player: a 1-block-wide, 2-block-tall box. Position (x, y) is the bottom-left corner
 * in block units; world y grows upward.
 */
final class Player {
    static final double WIDTH = 1.0;
    static final double HEIGHT = 2.0;

    // Tuning, in blocks and seconds.
    static final double GRAVITY = 34;
    static final double JUMP_SPEED = 10.2;       // ~1.5 block jump
    static final double MAX_FALL = 45;
    static final double WALK_SPEED = 6.5;
    static final double GROUND_ACCEL = 70;
    static final double AIR_ACCEL = 30;
    static final double FLY_SPEED = 12;
    static final double FLY_VERTICAL = 9;
    static final double FLY_ACCEL = 60;
    static final double COYOTE_TIME = 0.08;
    static final double JUMP_BUFFER = 0.12;
    static final long DOUBLE_TAP_MS = 300;

    private static final double EPS = 1e-7;

    double x;
    double y;
    double prevX;
    double prevY;
    double vx;
    double vy;
    boolean onGround;
    boolean flying;

    private double coyote;
    private double jumpBuffer;
    private long lastSpaceTap = Long.MIN_VALUE / 2;

    private final CollisionWindow window = new CollisionWindow();

    Player(double x, double y) {
        this.x = prevX = x;
        this.y = prevY = y;
    }

    /** Called for each discrete Space press (with its event timestamp). */
    void onSpacePressed(long when) {
        if (when - lastSpaceTap <= DOUBLE_TAP_MS) {
            flying = !flying;
            vy = 0;
            jumpBuffer = 0;
            lastSpaceTap = Long.MIN_VALUE / 2; // a third tap starts a fresh double-tap
        } else {
            lastSpaceTap = when;
            if (!flying) jumpBuffer = JUMP_BUFFER;
        }
    }

    /**
     * Advances one fixed physics step.
     *
     * @param moveX -1, 0 or 1 (A / D)
     * @param moveY -1, 0 or 1 (S / W, plus Space and Shift while flying)
     */
    void update(World world, double dt, int moveX, int moveY) {
        prevX = x;
        prevY = y;

        if (flying) {
            vx = approach(vx, moveX * FLY_SPEED, FLY_ACCEL * dt);
            vy = approach(vy, moveY * FLY_VERTICAL, FLY_ACCEL * dt);
        } else {
            double accel = onGround ? GROUND_ACCEL : AIR_ACCEL;
            vx = approach(vx, moveX * WALK_SPEED, accel * dt);
            vy = Math.max(vy - GRAVITY * dt, -MAX_FALL);

            coyote = onGround ? COYOTE_TIME : coyote - dt;
            if (jumpBuffer > 0) {
                jumpBuffer -= dt;
                if (coyote > 0) {
                    vy = JUMP_SPEED;
                    jumpBuffer = 0;
                    coyote = 0;
                }
            }
        }

        double dx = vx * dt;
        double dy = vy * dt;

        // Build the small dynamic collision window covering the swept box for this step.
        int minX = (int) Math.floor(Math.min(x, x + dx) + EPS);
        int maxX = (int) Math.floor(Math.max(x, x + dx) + WIDTH - EPS);
        int minY = (int) Math.floor(Math.min(y, y + dy) + EPS);
        int maxY = (int) Math.floor(Math.max(y, y + dy) + HEIGHT - EPS);
        window.load(world, minX, minY, maxX, maxY);

        moveX(dx);
        boolean wasFlying = flying;
        onGround = false;
        moveY(dy);

        // Like Minecraft: touching the ground while flying ends flight.
        if (wasFlying && onGround) flying = false;
    }

    private void moveX(double dx) {
        if (dx == 0) return;
        int rowMin = (int) Math.floor(y + EPS);
        int rowMax = (int) Math.floor(y + HEIGHT - EPS);
        if (dx > 0) {
            int first = (int) Math.floor(x + WIDTH - EPS) + 1;
            int last = (int) Math.floor(x + dx + WIDTH - EPS);
            for (int c = first; c <= last; c++) {
                if (columnBlocked(c, rowMin, rowMax)) {
                    x = c - WIDTH;
                    vx = 0;
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
                    return;
                }
            }
        }
        x += dx;
    }

    private void moveY(double dy) {
        if (dy == 0) return;
        int colMin = (int) Math.floor(x + EPS);
        int colMax = (int) Math.floor(x + WIDTH - EPS);
        if (dy > 0) {
            int first = (int) Math.floor(y + HEIGHT - EPS) + 1;
            int last = (int) Math.floor(y + dy + HEIGHT - EPS);
            for (int r = first; r <= last; r++) {
                if (rowBlocked(r, colMin, colMax)) {
                    y = r - HEIGHT;
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

    CollisionWindow window() {
        return window;
    }

    private static double approach(double v, double target, double step) {
        if (v < target) return Math.min(v + step, target);
        return Math.max(v - step, target);
    }
}
