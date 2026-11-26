package javabotics;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;

/** Game canvas: owns the world, the player, the camera and the fixed-timestep loop. */
final class Game extends Canvas implements Runnable {
    static final int TILE = 24;               // pixels per block
    static final double DT = 1.0 / 60.0;      // physics step
    static final double EDGE_MARGIN = 0.35;    // fraction of the view kept as a scroll margin
    static final int UNLOAD_RADIUS = 12;      // chunks kept around the camera

    private static final Color PLAYER_COLOR = new Color(128, 128, 128);
    private static final Color HUD_COLOR = new Color(90, 90, 90);
    private static final Color BACKGROUND_COLOR = new Color(222, 238, 255);
    private static final Color CREATURE_COLOR = new Color(205, 110, 40);
    private static final Color SELECT_COLOR = new Color(230, 30, 30);
    private static final Color BAR_COLOR = new Color(150, 150, 150);
    private static final Color SLOT_COLOR = new Color(195, 195, 195);
    private static final Color SLOT_BORDER = new Color(105, 105, 105);

    static final int SLOT_SIZE = 44;
    static final int SLOT_GAP = 6;
    static final int BAR_PAD = 6;
    static final int BAR_BOTTOM = 12;

    private final World world;
    private final Player player;
    private final Input input = new Input();
    private final Inventory inventory = new Inventory();
    private final EntityList entities;

    // Camera = world position of the bottom-left corner of the view, in blocks.
    private double camX;
    private double camY;
    private double prevCamX;
    private double prevCamY;
    private boolean cameraInitialised;

    private boolean showDebug;
    private double time;
    private int fps;
    private Thread thread;

    Game(long seed) {
        world = new World(seed);
        int spawnX = 0;
        int surface = world.surfaceY(spawnX, 256);
        player = new Player(spawnX, surface + 1);
        entities = new EntityList(seed);
        entities.add(player);

        setBackground(Color.WHITE);
        setIgnoreRepaint(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(input);
        addFocusListener(input.focusListener);
        addMouseListener(input.mouseListener);
        addMouseMotionListener(input.mouseListener);
        addMouseWheelListener(input.mouseListener);

        inventory.set(0, Chunk.SOLID);
    }

    void start() {
        requestFocus();
        thread = new Thread(this, "game-loop");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        createBufferStrategy(2);
        BufferStrategy bs = getBufferStrategy();

        long last = System.nanoTime();
        double acc = 0;
        int frames = 0;
        long fpsTimer = last;
        long unloadTimer = last;
        final long frameBudget = 1_000_000_000L / 144;

        while (true) {
            long now = System.nanoTime();
            acc += Math.min((now - last) / 1e9, 0.25);
            last = now;

            while (acc >= DT) {
                update();
                acc -= DT;
            }
            render(bs, acc / DT);
            frames++;

            if (now - fpsTimer >= 1_000_000_000L) {
                fps = frames;
                frames = 0;
                fpsTimer = now;
            }
            if (now - unloadTimer >= 2_000_000_000L) {
                world.unloadFarFrom(camX + viewW() / 2, camY + viewH() / 2, UNLOAD_RADIUS);
                unloadTimer = now;
            }

            long sleep = frameBudget - (System.nanoTime() - now);
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep / 1_000_000, (int) (sleep % 1_000_000));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ update

    private void update() {
        time += DT;
        for (Input.Press p; (p = input.pollPress()) != null; ) {
            switch (p.keyCode()) {
                case KeyEvent.VK_SPACE -> player.onSpacePressed(p.when());
                case KeyEvent.VK_W -> player.onJumpPressed();
                case KeyEvent.VK_F3 -> showDebug = !showDebug;
                case KeyEvent.VK_E -> spawnCreatureAtCursor();
                case KeyEvent.VK_ESCAPE -> System.exit(0);
                default -> {
                    if (p.keyCode() >= KeyEvent.VK_1 && p.keyCode() <= KeyEvent.VK_9) {
                        inventory.select(p.keyCode() - KeyEvent.VK_1);
                    }
                }
            }
        }
        for (Input.Click c; (c = input.pollClick()) != null; ) handleClick(c);

        player.moveX = (input.isDown(KeyEvent.VK_D) ? 1 : 0) - (input.isDown(KeyEvent.VK_A) ? 1 : 0);
        player.moveY = 0;
        if (player.flying) {
            boolean up = input.isDown(KeyEvent.VK_W) || input.isDown(KeyEvent.VK_SPACE);
            boolean down = input.isDown(KeyEvent.VK_S);
            player.moveY = (up ? 1 : 0) - (down ? 1 : 0);
        }
        player.sprint = input.isDown(KeyEvent.VK_SHIFT);
        entities.update(world, DT);
        updateCamera();
    }

    private void handleClick(Input.Click c) {
        if (c.wheel() != 0) {
            inventory.select(inventory.selected() + Integer.signum(c.wheel()));
            return;
        }
        int slot = hotbarSlotAt(c.x(), c.y());
        if (slot >= 0) {                              // clicks on the hotbar select a slot
            inventory.select(slot);
            return;
        }
        if (hotbarContains(c.x(), c.y())) return;

        int tx = cursorTileX(c.x());
        int ty = cursorTileY(c.y());

        if (c.button() == java.awt.event.MouseEvent.BUTTON1) {
            // Left click places whatever the selected slot holds - an empty slot is air,
            // so placing it into a block destroys that block. Blocks can go anywhere,
            // including mid-air, as long as no entity is in the way.
            placeBlock(tx, ty, inventory.selectedBlock());
        } else if (c.button() == java.awt.event.MouseEvent.BUTTON3) {
            placeBlock(tx, ty, Chunk.AIR);               // shortcut: always removes
        }
    }

    private void placeBlock(int tx, int ty, byte block) {
        if (world.getBlock(tx, ty) == block) return;
        if (block != Chunk.AIR && entities.anyOverlapsTile(tx, ty)) return;
        world.setBlock(tx, ty, block);
    }

    private void spawnCreatureAtCursor() {
        int mx = input.mouseX();
        int my = input.mouseY();
        if (mx < 0 || my < 0 || hotbarContains(mx, my)) return;
        entities.spawnCreature(world, cursorTileX(mx), cursorTileY(my));
    }

    private int cursorTileX(int px) {
        return (int) Math.floorDiv(Math.round(camX * TILE) + px, TILE);
    }

    private int cursorTileY(int py) {
        return (int) Math.floorDiv(Math.round(camY * TILE) + (getHeight() - py), TILE);
    }

    // ------------------------------------------------------------------ hotbar layout

    private int barWidth() {
        return Inventory.SLOTS * SLOT_SIZE + (Inventory.SLOTS - 1) * SLOT_GAP + 2 * BAR_PAD;
    }

    private int barHeight() {
        return SLOT_SIZE + 2 * BAR_PAD;
    }

    private int barX() {
        return (getWidth() - barWidth()) / 2;
    }

    private int barY() {
        return getHeight() - BAR_BOTTOM - barHeight();
    }

    private boolean hotbarContains(int x, int y) {
        return x >= barX() && x < barX() + barWidth() && y >= barY() && y < barY() + barHeight();
    }

    private int hotbarSlotAt(int x, int y) {
        int sy = barY() + BAR_PAD;
        if (y < sy || y >= sy + SLOT_SIZE) return -1;
        for (int i = 0; i < Inventory.SLOTS; i++) {
            int sx = barX() + BAR_PAD + i * (SLOT_SIZE + SLOT_GAP);
            if (x >= sx && x < sx + SLOT_SIZE) return i;
        }
        return -1;
    }

    /** Edge scrolling: the camera moves only when the player enters the margin at a screen edge. */
    private void updateCamera() {
        double vw = viewW();
        double vh = viewH();
        if (!cameraInitialised) {
            camX = player.x + Player.WIDTH / 2 - vw / 2;
            camY = player.y + Player.HEIGHT / 2 - vh / 2;
            prevCamX = camX;
            prevCamY = camY;
            cameraInitialised = true;
            return;
        }
        prevCamX = camX;
        prevCamY = camY;
        camX = follow(camX, player.x, Player.WIDTH, vw);
        camY = follow(camY, player.y, Player.HEIGHT, vh);
    }

    private static double follow(double cam, double pos, double size, double view) {
        double margin = Math.max(1.5, view * EDGE_MARGIN);
        if (2 * margin + size > view) {              // tiny window: just centre
            return pos + size / 2 - view / 2;
        }
        if (pos < cam + margin) return pos - margin;
        if (pos + size > cam + view - margin) return pos + size - view + margin;
        return cam;
    }

    private double viewW() {
        return Math.max(1, getWidth()) / (double) TILE;
    }

    private double viewH() {
        return Math.max(1, getHeight()) / (double) TILE;
    }

    // ------------------------------------------------------------------ render

    private void render(BufferStrategy bs, double alpha) {
        do {
            do {
                Graphics2D g = (Graphics2D) bs.getDrawGraphics();
                try {
                    draw(g, alpha);
                } finally {
                    g.dispose();
                }
            } while (bs.contentsRestored());
            bs.show();
        } while (bs.contentsLost());
        Toolkit.getDefaultToolkit().sync();
    }

    private void draw(Graphics2D g, double alpha) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        double cx = prevCamX + (camX - prevCamX) * alpha;
        double cy = prevCamY + (camY - prevCamY) * alpha;
        // Integer pixel offset of the camera keeps blocks crisp and jitter-free.
        long camPxX = Math.round(cx * TILE);
        long camPxY = Math.round(cy * TILE);

        // Visible tile range.
        int tx0 = (int) Math.floorDiv(camPxX, TILE);
        int ty0 = (int) Math.floorDiv(camPxY, TILE);
        int tx1 = (int) Math.floorDiv(camPxX + w, TILE);
        int ty1 = (int) Math.floorDiv(camPxY + h, TILE);

        // Background layer: light blue wherever generated terrain sits behind foreground air.
        g.setColor(BACKGROUND_COLOR);
        for (int ty = ty0; ty <= ty1; ty++) {
            int sy = (int) (h - ((long) (ty + 1) * TILE - camPxY));
            int tx = tx0;
            while (tx <= tx1) {
                if (!showsBackground(tx, ty)) {
                    tx++;
                    continue;
                }
                int start = tx;
                while (tx <= tx1 && showsBackground(tx, ty)) tx++;
                int sx = (int) ((long) start * TILE - camPxX);
                g.fillRect(sx, sy, (tx - start) * TILE, TILE);
            }
        }

        // Foreground blocks: merge horizontal runs into single rectangles.
        g.setColor(Color.BLACK);
        for (int ty = ty0; ty <= ty1; ty++) {
            int sy = (int) (h - ((long) (ty + 1) * TILE - camPxY));
            int tx = tx0;
            while (tx <= tx1) {
                if (!world.isSolid(tx, ty)) {
                    tx++;
                    continue;
                }
                int start = tx;
                while (tx <= tx1 && world.isSolid(tx, ty)) tx++;
                int sx = (int) ((long) start * TILE - camPxX);
                g.fillRect(sx, sy, (tx - start) * TILE, TILE);
            }
        }

        // Entities.
        for (Entity e : entities) {
            double ex = e.prevX + (e.x - e.prevX) * alpha;
            double ey = e.prevY + (e.y - e.prevY) * alpha;
            int esx = (int) (Math.round(ex * TILE) - camPxX);
            int esy = (int) (h - (Math.round((ey + e.height) * TILE) - camPxY));
            int ew = (int) Math.round(e.width * TILE);
            int eh = (int) Math.round(e.height * TILE);
            if (e instanceof Creature c) {
                g.setColor(CREATURE_COLOR);
                g.fillRect(esx, esy, ew, eh);
                // Eye on the facing side; it turns red while the creature can see the player.
                int eye = 7;
                int eyeX = c.facing > 0 ? esx + ew - eye - 3 : esx + 3;
                int eyeY = esy + 5;
                g.setColor(Color.WHITE);
                g.fillRect(eyeX, eyeY, eye, eye);
                g.setColor(c.seesPlayer ? SELECT_COLOR : Color.BLACK);
                g.fillRect(c.facing > 0 ? eyeX + 3 : eyeX + 1, eyeY + 2, 3, 3);
            } else {
                g.setColor(PLAYER_COLOR);
                g.fillRect(esx, esy, ew, eh);
            }
        }

        // Block under the cursor: red outline if it is not air.
        int mxp = input.mouseX();
        int myp = input.mouseY();
        if (mxp >= 0 && myp >= 0 && !hotbarContains(mxp, myp)) {
            int tx = (int) Math.floorDiv(camPxX + mxp, TILE);
            int ty = (int) Math.floorDiv(camPxY + (h - myp), TILE);
            if (world.isSolid(tx, ty)) {
                int sx = (int) ((long) tx * TILE - camPxX);
                int sy = (int) (h - ((long) (ty + 1) * TILE - camPxY));
                g.setColor(SELECT_COLOR);
                g.setStroke(new java.awt.BasicStroke(3));
                g.drawRect(sx + 1, sy + 1, TILE - 3, TILE - 3);
                g.setStroke(new java.awt.BasicStroke(1));
            }
        }

        drawHotbar(g);
        drawHud(g, camPxX, camPxY, h);
    }

    private boolean showsBackground(int tx, int ty) {
        return world.getBackground(tx, ty) != Chunk.AIR && !world.isSolid(tx, ty);
    }

    private void drawHotbar(Graphics2D g) {
        int bx = barX();
        int by = barY();
        g.setColor(BAR_COLOR);
        g.fillRect(bx, by, barWidth(), barHeight());
        g.setColor(SLOT_BORDER);
        g.drawRect(bx, by, barWidth() - 1, barHeight() - 1);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        for (int i = 0; i < Inventory.SLOTS; i++) {
            int sx = bx + BAR_PAD + i * (SLOT_SIZE + SLOT_GAP);
            int sy = by + BAR_PAD;
            g.setColor(SLOT_COLOR);
            g.fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE);

            byte block = inventory.get(i);
            if (block != Chunk.AIR) {
                int inset = 11;
                g.setColor(Color.BLACK);
                g.fillRect(sx + inset, sy + inset, SLOT_SIZE - 2 * inset, SLOT_SIZE - 2 * inset);
            }

            g.setColor(SLOT_BORDER);
            g.drawString(Integer.toString(i + 1), sx + 3, sy + 12);
            if (i == inventory.selected()) {
                g.setColor(Color.WHITE);
                g.setStroke(new java.awt.BasicStroke(3));
                g.drawRect(sx - 1, sy - 1, SLOT_SIZE + 1, SLOT_SIZE + 1);
                g.setStroke(new java.awt.BasicStroke(1));
            } else {
                g.drawRect(sx, sy, SLOT_SIZE - 1, SLOT_SIZE - 1);
            }
        }
    }

    private void drawHud(Graphics2D g, long camPxX, long camPxY, int h) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        int y = 18;
        if (time < 10 && !showDebug) {
            g.setColor(HUD_COLOR);
            g.drawString("A/D move  Shift sprint  W/Space jump  Space x2 fly  LMB place slot (empty = air)  RMB remove  1-9 slot  E creature  F3", 10, y);
        }
        if (!showDebug) return;

        // Vision lines from creatures that currently see the player.
        g.setColor(SELECT_COLOR);
        for (Entity e : entities) {
            if (e instanceof Creature c && c.seesPlayer) {
                g.drawLine((int) Math.round(c.eyeX * TILE - camPxX), (int) Math.round(h - (c.eyeY * TILE - camPxY)),
                        (int) Math.round(player.centerX() * TILE - camPxX), (int) Math.round(h - (player.centerY() * TILE - camPxY)));
            }
        }

        // Outline the dynamic collision window the player used this step.
        CollisionWindow cw = player.window();
        g.setColor(new Color(220, 40, 40));
        int wx = (int) ((long) cw.originX() * TILE - camPxX);
        int wy = (int) (h - ((long) (cw.originY() + cw.height()) * TILE - camPxY));
        g.drawRect(wx, wy, cw.width() * TILE, cw.height() * TILE);

        int bx = (int) Math.floor(player.x);
        String[] lines = {
                "FPS: " + fps,
                String.format("XY: %.2f / %.2f", player.x, player.y),
                String.format("Velocity: %.2f / %.2f", player.vx, player.vy),
                "Chunk: " + Math.floorDiv(bx, Chunk.SIZE) + ", " + Math.floorDiv((int) Math.floor(player.y), Chunk.SIZE),
                "Biome: " + world.generator().biomeAt(bx).displayName()
                        + " (region " + world.generator().regionOfChunk(Math.floorDiv(bx, Chunk.SIZE)) + ")",
                "Mode: " + (player.flying ? "flying" : (player.onGround ? "on ground" : "airborne")),
                "Collision window: " + cw.width() + "x" + cw.height() + " tiles",
                "Entities: " + entities.size(),
                "Loaded chunks: " + world.loadedChunks(),
                "Seed: " + world.generator().seed(),
        };
        g.setColor(Color.DARK_GRAY);
        for (String line : lines) {
            g.drawString(line, 10, y);
            y += 16;
        }
    }
}
