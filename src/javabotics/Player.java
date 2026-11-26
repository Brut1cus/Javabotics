package javabotics;

/** The player: a 1-block-wide, 2-block-tall gray box. */
final class Player extends Entity {
    static final double WIDTH = 1.0;
    static final double HEIGHT = 2.0;

    // Tuning, in blocks and seconds.
    static final double JUMP_SPEED = 10.2;       // ~1.5 block jump
    static final double WALK_SPEED = 8.5;
    static final double SPRINT_SPEED = 13;
    static final double GROUND_ACCEL = 70;
    static final double AIR_ACCEL = 30;
    static final double FLY_SPEED = 15;
    static final double FLY_SPRINT_SPEED = 24;
    static final double FLY_VERTICAL = 11;
    static final double FLY_ACCEL = 75;
    static final double COYOTE_TIME = 0.08;
    static final double JUMP_BUFFER = 0.12;
    static final long DOUBLE_TAP_MS = 300;

    boolean flying;

    // Control inputs, set by the game before each update.
    int moveX;      // -1, 0, 1 (A / D)
    int moveY;      // -1, 0, 1 (S / W and Space while flying)
    boolean sprint; // Shift

    private double coyote;
    private double jumpBuffer;
    private long lastSpaceTap = Long.MIN_VALUE / 2;

    Player(double x, double y) {
        super(x, y, WIDTH, HEIGHT, 2.0);
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

    /** Called for each discrete W press: a plain jump (no double-tap flight toggle). */
    void onJumpPressed() {
        if (!flying) jumpBuffer = JUMP_BUFFER;
    }

    @Override
    void update(World world, EntityList entities, double dt) {
        prevX = x;
        prevY = y;

        if (flying) {
            vx = approach(vx, moveX * (sprint ? FLY_SPRINT_SPEED : FLY_SPEED), FLY_ACCEL * dt);
            vy = approach(vy, moveY * FLY_VERTICAL, FLY_ACCEL * dt);
        } else {
            double accel = onGround ? GROUND_ACCEL : AIR_ACCEL;
            vx = approach(vx, moveX * (sprint ? SPRINT_SPEED : WALK_SPEED), accel * dt);
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

        boolean wasFlying = flying;
        move(world, vx * dt, vy * dt);
        // Like Minecraft: touching the ground while flying ends flight.
        if (wasFlying && onGround) flying = false;
    }
}
