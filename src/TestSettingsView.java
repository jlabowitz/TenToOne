import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 10: tests for SettingsView's ON/OFF toggle -- both that the
 * hit-test/mutation wiring in showBlocking() actually flips the live
 * GameSettings instance (not a copy), and that toggling twice returns to the
 * original state.
 */
public class TestSettingsView {

    @Test(timeout = 5000)
    public void clickingToggleFlipsTotalBetsCannotEqualTricksOnTheLiveInstance() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        GameSettings settings = new GameSettings();
        assertTrue("default is ON per GameSettings' own doc", settings.totalBetsCannotEqualTricks);

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 550, 135); // the toggle
            deliverClick(mouseInput, 700, 590); // Back
        });
        clicker.start();

        SettingsView.showBlocking(handler, mouseInput, toast, settings);
        clicker.join();

        assertFalse("one click must flip the toggle off", settings.totalBetsCannotEqualTricks);
    }

    @Test(timeout = 5000)
    public void clickingToggleTwiceReturnsToOriginalState() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        GameSettings settings = new GameSettings();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 550, 135); // toggle off
            deliverClick(mouseInput, 550, 135); // toggle back on
            deliverClick(mouseInput, 700, 590); // Back
        });
        clicker.start();

        SettingsView.showBlocking(handler, mouseInput, toast, settings);
        clicker.join();

        assertTrue(settings.totalBetsCannotEqualTricks);
    }

    private static void sleep50() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
