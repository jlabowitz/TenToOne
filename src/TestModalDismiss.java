import org.junit.Test;

import java.awt.Point;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for ModalDismiss (ROADMAP item 10 follow-up): the shared "click
 * outside the panel dismisses" glue every full-canvas showBlocking loop now
 * uses instead of a hand-copied isInsidePanel-negative-check.
 */
public class TestModalDismiss {

    @Test
    public void insideThePanelReturnsFalse() {
        boolean result = ModalDismiss.isOutsidePanel(new Point(50, 50), (x, y) -> true, "SomeView");
        assertFalse(result);
    }

    @Test
    public void outsideThePanelReturnsTrue() {
        boolean result = ModalDismiss.isOutsidePanel(new Point(999, 999), (x, y) -> false, "SomeView");
        assertTrue(result);
    }

    @Test
    public void passesTheClickCoordinatesThroughToTheHitTestUnchanged() {
        Point click = new Point(123, 456);
        boolean[] sawExpectedCoords = {false};
        ModalDismiss.isOutsidePanel(click, (x, y) -> {
            sawExpectedCoords[0] = (x == 123 && y == 456);
            return true;
        }, "SomeView");
        assertTrue(sawExpectedCoords[0]);
    }
}
