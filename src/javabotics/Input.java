package javabotics;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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

    private final ConcurrentLinkedQueue<Click> clicks = new ConcurrentLinkedQueue<>();
    private volatile boolean leftDown;
    private volatile boolean rightDown;
    private volatile int mouseX = -1;
    private volatile int mouseY = -1;

    record Press(int keyCode, long when) {}

    /** A mouse button press ({@link MouseEvent#BUTTON1} etc.) or a wheel step (button 0, amount != 0). */
    record Click(int button, int x, int y, int wheel) {}

    final MouseAdapter mouseListener = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            e.getComponent().requestFocusInWindow();
            mouseX = e.getX();
            mouseY = e.getY();
            if (e.getButton() == MouseEvent.BUTTON1) leftDown = true;
            if (e.getButton() == MouseEvent.BUTTON3) rightDown = true;
            clicks.add(new Click(e.getButton(), e.getX(), e.getY(), 0));
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) leftDown = false;
            if (e.getButton() == MouseEvent.BUTTON3) rightDown = false;
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            mouseX = e.getX();
            mouseY = e.getY();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            mouseX = -1;
            mouseY = -1;
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (e.getWheelRotation() != 0) clicks.add(new Click(0, e.getX(), e.getY(), e.getWheelRotation()));
        }
    };

    final FocusAdapter focusListener = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
            synchronized (down) {
                java.util.Arrays.fill(down, false);
                leftDown = false;
                rightDown = false;
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

    Click pollClick() {
        return clicks.poll();
    }

    /** Whether a mouse button ({@link MouseEvent#BUTTON1} or {@link MouseEvent#BUTTON3}) is held. */
    boolean isButtonDown(int button) {
        return button == MouseEvent.BUTTON1 ? leftDown : button == MouseEvent.BUTTON3 && rightDown;
    }

    /** Cursor position in canvas pixels, or -1 when the cursor is outside the canvas. */
    int mouseX() {
        return mouseX;
    }

    int mouseY() {
        return mouseY;
    }
}
