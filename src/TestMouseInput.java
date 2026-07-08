import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for MouseInput.awaitClickOrTimeout (ROADMAP item 10 follow-up): the
 * new timeout-bounded variant of awaitClick(), added so
 * Game.runStartScreen()'s blocking loop can interleave a poll for
 * StartScreen's Enter-to-submit flag between clicks. awaitClick() itself is
 * unchanged and not re-tested here.
 */
public class TestMouseInput {

    @Test(timeout = 5000)
    public void timesOutAndReturnsNullWhenNoClickArrives() {
        MouseInput mouseInput = new MouseInput();
        Point result = mouseInput.awaitClickOrTimeout(50);
        assertNull(result);
    }

    @Test(timeout = 5000)
    public void returnsTheClickWhenOneArrivesBeforeTheTimeout() throws InterruptedException {
        MouseInput mouseInput = new MouseInput();
        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 42, 99);
        });
        clicker.start();

        Point result = mouseInput.awaitClickOrTimeout(2000);

        clicker.join();
        assertEquals(new Point(42, 99), result);
    }

    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
