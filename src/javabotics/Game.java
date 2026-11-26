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
    static final double EDGE_MARGIN = 0.2;    // fraction of the view kept as a scroll margin
    static final int UNLOAD_RADIUS = 12;      // chunks kept around the camera

    private static final Color PLAYER_COLOR = new Color(128, 128, 128);
    private static final Color HUD_COLOR = new Color(90, 90, 90);

    private final World world;
    private final Player player;
    private final Input input = new Input();

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

        setBackground(Color.WHITE);
        setIgnoreRepaint(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(input);
        addFocusListener(input.focusListener);
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
                case KeyEvent.VK_F3 -> showDebug = !showDebug;
                case KeyEvent.VK_ESCAPE -> System.exit(0);
                default -> { }
            }
        }

        int mx = (input.isDown(KeyEvent.VK_D) ? 1 : 0) - (input.isDown(KeyEvent.VK_A) ? 1 : 0);
        int my = 0;
        if (player.flying) {
            boolean up = input.isDown(KeyEvent.VK_W) || input.isDown(KeyEvent.VK_SPACE);
            boolean down = input.isDown(KeyEvent.VK_S) || input.isDown(KeyEvent.VK_SHIFT);
            my = (up ? 1 : 0) - (down ? 1 : 0);
        }
        player.update(world, DT, mx, my);
        updateCamera();
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
        double margin = Math.max(2, view * EDGE_MARGIN);
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

        // Blocks: merge horizontal runs into single rectangles.
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

        // Player.
        double px = player.prevX + (player.x - player.prevX) * alpha;
        double py = player.prevY + (player.y - player.prevY) * alpha;
        int psx = (int) (Math.round(px * TILE) - camPxX);
        int psy = (int) (h - (Math.round((py + Player.HEIGHT) * TILE) - camPxY));
        g.setColor(PLAYER_COLOR);
        g.fillRect(psx, psy, (int) (Player.WIDTH * TILE), (int) (Player.HEIGHT * TILE));

        drawHud(g, camPxX, camPxY, h);
    }

    private void drawHud(Graphics2D g, long camPxX, long camPxY, int h) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        int y = 18;
        if (time < 10 && !showDebug) {
            g.setColor(HUD_COLOR);
            g.drawString("A/D move   Space jump   Space x2 toggle fly (W/S or Space/Shift)   F3 debug", 10, y);
        }
        if (!showDebug) return;

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
