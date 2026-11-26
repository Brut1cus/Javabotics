package javabotics;

/** Line-of-sight queries through the foreground block grid. */
final class Vision {
    private Vision() {}

    /**
     * True if the segment from (x0, y0) to (x1, y1) passes through no solid foreground tile.
     * Walks exactly the tiles the segment crosses (Amanatides-Woo grid traversal), so the cost
     * is proportional to the distance in tiles.
     */
    static boolean lineOfSight(World world, double x0, double y0, double x1, double y1) {
        int tx = (int) Math.floor(x0);
        int ty = (int) Math.floor(y0);
        int ex = (int) Math.floor(x1);
        int ey = (int) Math.floor(y1);
        double dx = x1 - x0;
        double dy = y1 - y0;
        int sx = dx > 0 ? 1 : -1;
        int sy = dy > 0 ? 1 : -1;
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? tx + 1 - x0 : x0 - tx) * tDeltaX;
        double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? ty + 1 - y0 : y0 - ty) * tDeltaY;

        int steps = Math.abs(ex - tx) + Math.abs(ey - ty) + 1;
        for (int i = 0; i < steps; i++) {
            if (world.isSolid(tx, ty)) return false;
            if (tx == ex && ty == ey) return true;
            if (tMaxX < tMaxY) {
                tMaxX += tDeltaX;
                tx += sx;
            } else {
                tMaxY += tDeltaY;
                ty += sy;
            }
        }
        return true;
    }
}
