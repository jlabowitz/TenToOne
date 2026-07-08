import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for RulesView.isBackButton, the only interactive hit-test this class
 * exposes (ROADMAP item 1). Layout contract: Back covers x in [680, 760),
 * y in [536, 562) (ROADMAP item 10 follow-up: moved up from 576/602 as part
 * of shrinking PANEL_H 590 -> 548 -- see that class's own doc).
 */
public class TestRulesView {
    private static final int TOP = 536;
    private static final int BOTTOM = 562;
    private static final int LEFT = 680;
    private static final int RIGHT = 760;

    @Test
    public void clickInsideBackReturnsTrue() {
        RulesView view = new RulesView();
        assertTrue(view.isBackButton(720, 550));
    }

    @Test
    public void leftAndTopBoundaryIsInclusive() {
        RulesView view = new RulesView();
        assertTrue(view.isBackButton(LEFT, TOP));
    }

    @Test
    public void rightAndBottomBoundaryIsExclusive() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(RIGHT, 550));
        assertFalse(view.isBackButton(720, BOTTOM));
        // last in-bounds pixel still hits
        assertTrue(view.isBackButton(RIGHT - 1, BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowButtonReturnsFalse() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(720, TOP - 1));
        assertFalse(view.isBackButton(720, BOTTOM));
    }

    @Test
    public void clickLeftOrRightOfButtonReturnsFalse() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(LEFT - 1, 550));
        assertFalse(view.isBackButton(RIGHT, 550));
    }

    @Test
    public void clickElsewhereOnCanvasReturnsFalse() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(0, 0));
        assertFalse(view.isBackButton(400, 300));
    }

    // --- ROADMAP item 10 follow-up: click-outside-dismiss, via the shared
    // ModalDismiss helper -- same convention AchievementsView/PauseView
    // already had, added here for the first time (SettingsView gets the same
    // treatment; see TestSettingsView). Panel bounds: 40,20,760,548.

    private static final int PANEL_LEFT = 40, PANEL_TOP = 20;
    private static final int PANEL_RIGHT = 800, PANEL_BOTTOM = 568;

    @Test
    public void isInsidePanelReflectsTheFullCanvasBounds() {
        RulesView view = new RulesView();
        assertTrue(view.isInsidePanel(PANEL_LEFT, PANEL_TOP));
        assertTrue(view.isInsidePanel(PANEL_RIGHT - 1, PANEL_BOTTOM - 1));
        assertFalse(view.isInsidePanel(PANEL_LEFT - 1, PANEL_TOP));
        assertFalse(view.isInsidePanel(PANEL_LEFT, PANEL_TOP - 1));
        assertFalse(view.isInsidePanel(PANEL_RIGHT, PANEL_TOP));
        assertFalse(view.isInsidePanel(PANEL_LEFT, PANEL_BOTTOM));
    }

    @Test(timeout = 5000)
    public void clickOutsidePanelDismissesWithNoAction() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 820, 615); // well outside the panel
        });
        clicker.start();

        RulesView.showBlocking(handler, mouseInput, toast);
        clicker.join();
        // no exception / hang -- showBlocking returned, proving the miss-click resolved it
    }

    /**
     * Code-review Finding 1 (achievement-toast click-to-dismiss): showBlocking's
     * click loop must dismiss a showing AchievementToast before it ever reaches
     * the Back-button check, mirroring the exact wiring Human's three loops
     * already had. AchievementToast.isToastHotspot()/dismiss()'s own semantics
     * are already exhaustively covered by TestAchievementToast -- this only
     * smoke-tests that showBlocking's loop actually wires them in. Feeds a
     * click inside the toast's dismiss band first, then the real Back-button
     * click, from a background thread (same sleep-then-act idiom TestGame's
     * stopFromExternalThreadDoesNotHangAndFlipsRunningFalse already uses for a
     * comparable cross-thread readiness assumption): if the dismiss click had
     * fallen through to isBackButton() unhandled, this would hang (Back is
     * never actually clicked again) and fail via the JUnit timeout rather than
     * silently passing.
     */
    @Test(timeout = 5000)
    public void showBlockingDismissesToastHotspotClickBeforeCheckingBackButton() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Test Achievement");
        toast.tick();
        assertTrue("toast must actually be showing for isToastHotspot() to fire", toast.isShowingSomething());

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 400, 20); // inside the toast's dismiss band (y < 50), clear of the Back button
            deliverClick(mouseInput, 720, 550); // the real Back button
        });
        clicker.start();

        RulesView.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertFalse("the first click (inside the toast band) must dismiss the toast rather than being ignored",
                toast.isShowingSomething());
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
