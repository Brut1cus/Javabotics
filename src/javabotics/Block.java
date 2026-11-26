package javabotics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;

/**
 * Block types. Every non-air block is solid for collision.
 *
 * <ul>
 *   <li><b>Ground</b>: terrain, produced by world generation.</li>
 *   <li><b>Joint</b>: will become a powered connection between the two sides of a walker.</li>
 *   <li><b>Scaffold</b>: a building aid that will never be included in a walker; it
 *       separates blocks that should not be assembled together.</li>
 * </ul>
 */
final class Block {
    private Block() {}

    static final byte AIR = 0;
    static final byte GROUND = 1;
    static final byte JOINT = 2;
    static final byte SCAFFOLD = 3;

    private static final Color JOINT_BODY = new Color(72, 72, 72);
    private static final Color JOINT_RING = new Color(215, 215, 215);
    private static final Color JOINT_PLATE = new Color(150, 150, 150);
    private static final Color SCAFFOLD_COLOR = new Color(115, 115, 115);

    static String name(byte b) {
        return switch (b) {
            case AIR -> "Air";
            case GROUND -> "Ground";
            case JOINT -> "Joint";
            case SCAFFOLD -> "Scaffold";
            default -> "Unknown";
        };
    }

    /** Opaque blocks completely hide the background layer behind them. */
    static boolean isOpaque(byte b) {
        return b == GROUND || b == JOINT;
    }

    /** Whether the block type may be placed in the background layer. */
    static boolean allowedInBackground(byte b) {
        return b == AIR || b == GROUND;
    }

    /** Draws one block of type {@code b} into the square at (x, y) with side {@code s} pixels. */
    static void draw(Graphics2D g, byte b, int x, int y, int s) {
        switch (b) {
            case GROUND -> {
                g.setColor(Color.BLACK);
                g.fillRect(x, y, s, s);
            }
            case JOINT -> {
                g.setColor(JOINT_BODY);
                g.fillRect(x, y, s, s);
                // Mounting plates on the two sides the joint connects.
                int pw = Math.max(2, s / 8);
                g.setColor(JOINT_PLATE);
                g.fillRect(x, y + s / 4, pw, s - s / 2);
                g.fillRect(x + s - pw, y + s / 4, pw, s - s / 2);
                // Axle.
                Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = Math.round(s * 0.58f);
                g.setColor(JOINT_RING);
                g.fillOval(x + (s - d) / 2, y + (s - d) / 2, d, d);
                int d2 = Math.max(3, Math.round(s * 0.22f));
                g.setColor(Color.BLACK);
                g.fillOval(x + (s - d2) / 2, y + (s - d2) / 2, d2, d2);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
            }
            case SCAFFOLD -> {
                // Open frame with cross-bracing: the background shows through.
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke(Math.max(2, s / 12f)));
                g.setColor(SCAFFOLD_COLOR);
                int i = 1;
                g.drawRect(x + i, y + i, s - 2 * i - 1, s - 2 * i - 1);
                g.drawLine(x + i, y + i, x + s - i - 1, y + s - i - 1);
                g.drawLine(x + s - i - 1, y + i, x + i, y + s - i - 1);
                g.setStroke(old);
            }
            default -> { }
        }
    }
}
