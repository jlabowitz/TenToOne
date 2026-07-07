import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 10: wiring test for PauseView.showBlocking's Resume button
 * and toast-dismiss precedence, same pattern as every other showBlocking
 * test in this codebase (see TestRulesView).
 */
public class TestPauseView {

    @Test(timeout = 5000)
    public void resumeButtonResolvesTheBlockingCall() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 700, 590); // Resume button (same geometry as RulesView's Back)
        });
        clicker.start();

        PauseView.showBlocking(handler, mouseInput, toast);
        clicker.join();
        // no exception / hang -- showBlocking returned, proving the Resume click resolved it
    }

    @Test(timeout = 5000)
    public void toastHotspotIsDismissedBeforeCheckingResumeButton() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Test Achievement");
        toast.tick();
        assertTrue("toast must actually be showing for isToastHotspot() to fire", toast.isShowingSomething());

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 400, 20); // inside the toast's dismiss band
            deliverClick(mouseInput, 700, 590); // Resume
        });
        clicker.start();

        PauseView.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertTrue("toast dismiss click must not have been swallowed by the Resume check",
                !toast.isShowingSomething());
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
