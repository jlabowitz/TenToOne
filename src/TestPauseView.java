import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 10: wiring test for PauseView.showBlocking's Resume button
 * and toast-dismiss precedence, same pattern as every other showBlocking
 * test in this codebase (see TestRulesView).
 *
 * ROADMAP item 10 follow-up: PauseView shrank to a small centered dialog
 * (300x160, centered on the 840x630 canvas) and gained click-outside-
 * dismiss (mirroring HamburgerMenu's own miss-click convention) -- the
 * earlier Resume-only dismiss meant a miss-click looped forever. Resume's
 * geometry moved accordingly (PANEL_X=270, PANEL_Y=235; Resume at
 * x=[330,450), y=[339,375)).
 *
 * Bug fix (this pass): Resume's slot was never actually centered on the
 * panel (60px from the left edge for a 120px button in a 300px-wide panel --
 * centering needs 90px) despite the title above it being centered on that
 * same full panel width -- Resume moved to x=[360,480) to match.
 */
public class TestPauseView {

    private static final int RESUME_LEFT = 360, RESUME_RIGHT = 480;
    private static final int RESUME_TOP = 339, RESUME_BOTTOM = 375;

    @Test(timeout = 5000)
    public void resumeButtonResolvesTheBlockingCall() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 420, 350); // Resume button
        });
        clicker.start();

        PauseView.showBlocking(handler, mouseInput, toast);
        clicker.join();
        // no exception / hang -- showBlocking returned, proving the Resume click resolved it
    }

    @Test(timeout = 5000)
    public void clickOutsidePanelResolvesTheBlockingCall() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 700, 590); // well outside the new, smaller centered panel
        });
        clicker.start();

        PauseView.showBlocking(handler, mouseInput, toast);
        clicker.join();
        // no exception / hang -- showBlocking returned, proving the miss-click still resolved it
    }

    @Test
    public void isResumeButtonReflectsTheNewCenteredGeometry() {
        PauseView view = new PauseView();
        assertTrue(view.isResumeButton(RESUME_LEFT, RESUME_TOP));
        assertTrue(view.isResumeButton(RESUME_RIGHT - 1, RESUME_BOTTOM - 1));
        assertTrue(!view.isResumeButton(RESUME_LEFT - 1, RESUME_TOP));
        assertTrue(!view.isResumeButton(RESUME_LEFT, RESUME_TOP - 1));
        assertTrue(!view.isResumeButton(RESUME_RIGHT, RESUME_TOP));
        assertTrue(!view.isResumeButton(RESUME_LEFT, RESUME_BOTTOM));
    }

    @Test
    public void isInsidePanelReflectsTheNewCenteredBounds() {
        PauseView view = new PauseView();
        assertTrue(view.isInsidePanel(270, 235));
        assertTrue(view.isInsidePanel(569, 394));
        assertTrue(!view.isInsidePanel(269, 235));
        assertTrue(!view.isInsidePanel(270, 234));
        assertTrue(!view.isInsidePanel(570, 235));
        assertTrue(!view.isInsidePanel(270, 395));
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
            deliverClick(mouseInput, 420, 350); // Resume
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
