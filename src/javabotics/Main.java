package javabotics;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

/** Entry point: opens a resizable window hosting the game canvas. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        long seed = args.length > 0 ? parseSeed(args[0]) : System.nanoTime();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Javabotics");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);
            frame.getContentPane().setBackground(Color.WHITE);

            Game game = new Game(seed);
            game.setPreferredSize(new Dimension(1280, 720));
            frame.getContentPane().add(game, BorderLayout.CENTER);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.start();
        });
    }

    private static long parseSeed(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }
}
