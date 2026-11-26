package javabotics;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Keyboard state shared between the AWT event thread and the game thread.
 * Held keys are exposed as flags; discrete presses (Space, F3) are queued with their
 * timestamps so the game thread can detect double-taps precisely.
 */
final class Input extends KeyAdapter {
    private final boolean[] down = new boolean[KeyEvent.KEY_LAST + 1];
    private final long[] releasedAt = new long[KeyEvent.KEY_LAST + 1];
    private final ConcurrentLinkedQueue<Press> presses = new ConcurrentLinkedQueue<>();

    record Press(int keyCode, long when) {}

    final FocusAdapter focusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            synchronized (down) {
                java.util.Arrays.fill(down, false);
            }
        }
    };

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code < 0 || code >= down.length) return;
        synchronized (down) {
            // Ignore OS auto-repeat: either the key is still down, or (on some X11 setups)
            // a synthetic release arrived within a couple of milliseconds.
            boolean repeat = down[code] || e.getWhen() - releasedAt[code] <= 2;
            down[code] = true;
            if (!repeat) presses.add(new Press(code, e.getWhen()));
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code < 0 || code >= down.length) return;
        synchronized (down) {
            down[code] = false;
            releasedAt[code] = e.getWhen();
        }
    }

    boolean isDown(int code) {
        synchronized (down) {
            return down[code];
        }
    }

    Press pollPress() {
        return presses.poll();
    }
}
