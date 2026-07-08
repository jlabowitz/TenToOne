import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 10: tests for HamburgerMenu's item-selection and Restart
 * confirmation flow. Uses the same background-thread-delivers-clicks pattern
 * every other showBlocking test in this codebase uses (see
 * TestRulesView/TestHumanBet).
 *
 * ROADMAP item 10 follow-up: rewritten for the new single-column (1x6) layout
 * and reordered menu items (Pause, Rules, Settings, Achievements, Restart
 * Game, Go to Menu) -- see HamburgerMenu's own class doc for why the earlier
 * 3x2 grid was replaced.
 */
public class TestHamburgerMenu {

    @Test(timeout = 5000)
    public void selectingRulesReturnsRulesWithoutConfirmation() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 30, 50); // row 1: Rules
        });
        clicker.start();

        HamburgerMenu.Selection selection = HamburgerMenu.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertEquals(HamburgerMenu.Selection.RULES, selection);
    }

    @Test(timeout = 5000)
    public void clickingOutsidePanelDismissesWithNoSelection() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 500, 500); // far outside the panel
        });
        clicker.start();

        HamburgerMenu.Selection selection = HamburgerMenu.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertNull(selection);
    }

    /**
     * ROADMAP item 10's own explicit spec: Restart must not act on the first
     * click -- selecting it must show a confirmation instead, and only a
     * subsequent Yes click resolves to Selection.RESTART.
     */
    @Test(timeout = 5000)
    public void restartRequiresConfirmationBeforeResolving() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        HamburgerMenu menu = new HamburgerMenu();
        // Directly exercise the item-picking behavior without threading a
        // background clicker for the confirmation-state assertions below --
        // itemAt() is public and can be checked directly against a fresh
        // instance's known geometry: single column, GRID_LEFT=20,
        // ROW_WIDTH=130, ROW_TOP=11, ROW_HEIGHT=30. "Restart Game" is row 4,
        // spanning y=[131,161).
        assertEquals(HamburgerMenu.Selection.RESTART, menu.itemAt(30, 145));

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 30, 145); // Restart Game (row 4)
            deliverClick(mouseInput, 350, 340); // Yes
        });
        clicker.start();

        HamburgerMenu.Selection selection = HamburgerMenu.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertEquals(HamburgerMenu.Selection.RESTART, selection);
    }

    @Test(timeout = 5000)
    public void restartConfirmationNoDismissesWithNoSelection() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 30, 145); // Restart Game (row 4)
            deliverClick(mouseInput, 470, 340); // No
        });
        clicker.start();

        HamburgerMenu.Selection selection = HamburgerMenu.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertNull(selection);
    }

    @Test(timeout = 5000)
    public void toastHotspotIsDismissedBeforeAnyMenuHandling() throws InterruptedException {
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
            deliverClick(mouseInput, 30, 50); // row 1: Rules
        });
        clicker.start();

        HamburgerMenu.Selection selection = HamburgerMenu.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertEquals(HamburgerMenu.Selection.RULES, selection);
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
