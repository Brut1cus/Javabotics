package javabotics;

import java.util.SplittableRandom;

/**
 * A simple 1x1 creature. It wanders, hops up single-block steps, and has vision: a forward
 * cone limited by range and blocked by foreground blocks. When it sees the player it chases
 * them, and keeps heading to where it last saw them for a few seconds after losing sight.
 */
final class Creature extends Entity {
    static final double SIZE = 1.0;
    static final double WALK_SPEED = 3.0;
    static final double CHASE_SPEED = 6.0;
    static final double ACCEL = 40;
    static final double JUMP_SPEED = 9.6;           // clears a 1-block step
    static final double VISION_RANGE = 18;
    static final double VISION_HALF_ANGLE_COS = Math.cos(Math.toRadians(70));
    static final double MEMORY_TIME = 3.0;

    int facing = 1;
    boolean seesPlayer;
    /** Point the creature last looked at the player from / to (for the debug overlay). */
    double eyeX;
    double eyeY;

    private final SplittableRandom rng;
    private double memory;
    private double lastSeenX;
    private int wanderDir;
    private double wanderTimer;
    private int failedJumps;

    Creature(double x, double y, long seed) {
        super(x, y, SIZE, SIZE, 1.0);
        rng = new SplittableRandom(seed);
        facing = rng.nextBoolean() ? 1 : -1;
    }

    @Override
    void update(World world, EntityList entities, double dt) {
        prevX = x;
        prevY = y;

        Player player = entities.player();
        seesPlayer = player != null && canSee(world, player);
        if (seesPlayer) {
            memory = MEMORY_TIME;
            lastSeenX = player.centerX();
        } else {
            memory -= dt;
        }

        int dir;
        double speed;
        if (memory > 0) {
            double d = lastSeenX - centerX();   // stop once touching (centres ~1 block apart)
            dir = Math.abs(d) < 1.15 ? 0 : (int) Math.signum(d);
            speed = CHASE_SPEED;
        } else {
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                wanderDir = rng.nextInt(10) < 3 ? 0 : (rng.nextBoolean() ? 1 : -1);
                wanderTimer = 1 + rng.nextDouble() * 2.5;
            }
            dir = wanderDir;
            speed = WALK_SPEED;
        }
        if (dir != 0) facing = dir;

        vx = approach(vx, dir * speed, ACCEL * dt);
        vy = Math.max(vy - GRAVITY * dt, -MAX_FALL);

        // Hop when walking into a wall; give up and turn around if the wall is too tall.
        if (onGround && hitWall && dir != 0) {
            if (failedJumps < 2) {
                vy = JUMP_SPEED;
                failedJumps++;
            } else if (memory <= 0) {
                wanderDir = -wanderDir;
                failedJumps = 0;
            }
        } else if (onGround && !hitWall) {
            failedJumps = 0;
        }

        move(world, vx * dt, vy * dt);
    }

    /** Vision: within range, inside the forward cone, and not blocked by foreground blocks. */
    boolean canSee(World world, Entity target) {
        eyeX = x + (facing > 0 ? 0.75 : 0.25);
        eyeY = y + 0.7;
        double dx = target.centerX() - eyeX;
        double dy = target.centerY() - eyeY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > VISION_RANGE) return false;
        if (dist > 1.5 && dx * facing / dist < VISION_HALF_ANGLE_COS) return false;
        // Visible if either the top or the bottom of the target is unobstructed.
        double tx = target.centerX();
        return Vision.lineOfSight(world, eyeX, eyeY, tx, target.y + target.height * 0.85)
                || Vision.lineOfSight(world, eyeX, eyeY, tx, target.y + target.height * 0.15);
    }
}
