import org.junit.After;
import org.junit.Test;

import javax.swing.JFrame;
import java.awt.Canvas;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for the canvas-sizing fix in Window.java.
 *
 * Previously Window sized the JFrame directly to WIDTHxHEIGHT. Because a
 * JFrame's size includes OS-drawn chrome (title bar/borders), this left the
 * actual drawable Canvas smaller than requested -- clipping the leftmost
 * card of the human's hand. The fix sizes the Canvas itself and lets
 * frame.pack() grow the frame around it, so the Canvas (not the frame)
 * should end up exactly Game.WIDTH x Game.HEIGHT.
 *
 * This exercises Window.buildFrame(...) directly -- the same pack()-based
 * sizing logic the production Window constructor uses -- stopping short of
 * frame.setVisible(true)/game.start(), so no window is ever shown and no
 * render thread is ever started during this test. A plain Canvas stands in
 * for the Game canvas since the sizing logic only touches Component methods;
 * this sidesteps needing a real Game, whose own constructor unconditionally
 * builds (and would show) its own Window.
 *
 * Like the rest of this AWT-heavy suite, this test requires a real display
 * environment: constructing a JFrame/Canvas throws HeadlessException on a
 * machine with no display server at all. There's no CI for this project, so
 * that's a documentation note here, not a functional gate.
 */
public class TestWindowSizing {

    private JFrame frame;

    @After
    public void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }

    @Test
    public void canvasIsSizedToRequestedDimensionsNotShrunkByFrameChrome() {
        Canvas canvas = new Canvas();

        frame = Window.buildFrame(Game.WIDTH, Game.HEIGHT, "Ten to One", canvas);

        assertEquals("canvas width should equal the requested width, not be shrunk by frame chrome",
                Game.WIDTH, canvas.getWidth());
        assertEquals("canvas height should equal the requested height, not be shrunk by frame chrome",
                Game.HEIGHT, canvas.getHeight());
    }
}
