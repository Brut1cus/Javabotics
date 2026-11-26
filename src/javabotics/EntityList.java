package javabotics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * All dynamic entities in the world (the player included). Updates them, resolves
 * entity-vs-entity collisions, and spawns / despawns creatures around the player.
 */
final class EntityList implements Iterable<Entity> {
    static final int MAX_NATURAL_CREATURES = 6;
    static final double SPAWN_INTERVAL = 3.0;
    static final double DESPAWN_DISTANCE = 96;

    private final List<Entity> entities = new ArrayList<>();
    private final SplittableRandom rng;
    private Player player;
    private double spawnTimer = 0.5;

    EntityList(long seed) {
        rng = new SplittableRandom(seed ^ 0xC0FFEE);
    }

    void add(Entity e) {
        entities.add(e);
        if (e instanceof Player p) player = p;
    }

    Player player() {
        return player;
    }

    int size() {
        return entities.size();
    }

    @Override
    public Iterator<Entity> iterator() {
        return entities.iterator();
    }

    long nextSeed() {
        return rng.nextLong();
    }

    boolean anyOverlapsTile(int tx, int ty) {
        for (Entity e : entities) if (e.overlapsTile(tx, ty)) return true;
        return false;
    }

    void update(World world, double dt) {
        for (int i = 0; i < entities.size(); i++) entities.get(i).update(world, this, dt);
        resolveCollisions(world);

        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = SPAWN_INTERVAL;
            trySpawnNatural(world);
        }

        Iterator<Entity> it = entities.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            if (e != player && player != null
                    && Math.abs(e.centerX() - player.centerX()) + Math.abs(e.centerY() - player.centerY()) > DESPAWN_DISTANCE) {
                e.removed = true;
            }
            if (e.removed) it.remove();
        }
    }

    // ------------------------------------------------------------------ entity collisions

    private void resolveCollisions(World world) {
        int n = entities.size();
        for (int i = 0; i < n; i++) {
            Entity a = entities.get(i);
            for (int j = i + 1; j < n; j++) {
                Entity b = entities.get(j);
                if (a.overlaps(b)) separate(world, a, b);
            }
        }
    }

    /**
     * Pushes two overlapping bodies apart along the axis of least overlap. Vertically the
     * upper body is lifted onto the lower one (so you can stand on creatures and they on
     * you); horizontally both are pushed, split by mass. Pushes go through world collision,
     * so nobody is shoved into a wall.
     */
    private static void separate(World world, Entity a, Entity b) {
        double ox = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
        double oy = Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y);
        if (oy < ox) {
            Entity upper = a.centerY() > b.centerY() ? a : b;
            push(world, upper, 0, oy);
            if (upper.vy < 0) upper.vy = 0;
            upper.onGround = true;
        } else {
            double total = a.mass + b.mass;
            double side = a.centerX() < b.centerX() ? -1 : 1;
            push(world, a, side * ox * b.mass / total, 0);
            push(world, b, -side * ox * a.mass / total, 0);
        }
    }

    private static void push(World world, Entity e, double dx, double dy) {
        boolean ground = e.onGround;
        boolean wall = e.hitWall;
        e.move(world, dx, dy);
        e.onGround |= ground;
        e.hitWall = wall;
    }

    // ------------------------------------------------------------------ spawning

    /** Spawns a creature standing at tile (tx, ty) if both it and its spot are free. */
    Creature spawnCreature(World world, int tx, int ty) {
        if (world.isSolid(tx, ty) || anyOverlapsTile(tx, ty)) return null;
        Creature c = new Creature(tx, ty, nextSeed());
        add(c);
        return c;
    }

    private void trySpawnNatural(World world) {
        if (player == null) return;
        int creatures = entities.size() - 1;
        if (creatures >= MAX_NATURAL_CREATURES) return;
        double side = rng.nextBoolean() ? 1 : -1;
        int tx = (int) Math.floor(player.centerX() + side * (30 + rng.nextDouble() * 20));
        int top = (int) Math.floor(player.y) + 40;
        int ground = world.surfaceY(tx, top);
        if (ground >= top || ground < player.y - 60) return; // buried or bottomless: skip
        spawnCreature(world, tx, ground + 1);
    }
}
