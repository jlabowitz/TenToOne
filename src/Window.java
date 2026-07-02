import javax.swing.*;
import java.awt.*;

public class Window {

    public Window(int width, int height, String title, Game game) {
        JFrame frame = buildFrame(width, height, title, game);
        frame.setVisible(true);
        game.start();
    }

    /**
     * Builds and packs the JFrame around {@code canvas}, sized so the canvas
     * (not the frame) ends up exactly width x height -- without showing the
     * frame or starting anything. Split out from the constructor as its own
     * step so this sizing behavior can be exercised by a test without
     * popping a window on screen (see TestWindowSizing).
     */
    static JFrame buildFrame(int width, int height, String title, Component canvas) {
        JFrame frame = new JFrame(title);

        //size the Canvas itself, not the frame: a JFrame's size includes
        //OS-drawn chrome (title bar, borders), so sizing the frame directly
        //leaves the actual drawable Canvas smaller than requested. pack()
        //below grows the frame to fit the canvas's preferred size instead.
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.setMaximumSize(new Dimension(width, height));
        canvas.setMinimumSize(new Dimension(width, height));

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        return frame;
    }

}
