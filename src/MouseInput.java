import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Bridges mouse clicks from the AWT event thread to the game-logic thread.
 * Clicks are queued on the event thread and consumed with blocking takes on
 * the game-logic thread, so no other shared state is touched across threads.
 */
public class MouseInput extends MouseAdapter {
    private final LinkedBlockingQueue<Point> clicks = new LinkedBlockingQueue<>();

    @Override
    public void mousePressed(MouseEvent e) {
        //only left-clicks play cards
        if (e.getButton() == MouseEvent.BUTTON1) {
            clicks.offer(e.getPoint());
        }
    }

    /** Blocks until the next click and returns its canvas coordinates. */
    public Point awaitClick() {
        try {
            return clicks.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a mouse click", e);
        }
    }

    /** Drops any clicks queued while it wasn't the human's turn. */
    public void clearClicks() {
        clicks.clear();
    }
}
