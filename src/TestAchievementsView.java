import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for AchievementsView (ROADMAP item 2): the full-list Achievements
 * modal. Reuses RulesView's exact Back-button geometry/lifecycle shape (see
 * class doc), so this locks in the same contract TestRulesView does.
 */
public class TestAchievementsView {
    private static final int TOP = 576;
    private static final int BOTTOM = 602;
    private static final int LEFT = 680;
    private static final int RIGHT = 760;

    @Test
    public void clickInsideBackReturnsTrue() {
        AchievementsView view = new AchievementsView(SaveData.defaults());
        assertTrue(view.isBackButton(720, 590));
    }

    @Test
    public void leftAndTopBoundaryIsInclusive() {
        AchievementsView view = new AchievementsView(SaveData.defaults());
        assertTrue(view.isBackButton(LEFT, TOP));
    }

    @Test
    public void rightAndBottomBoundaryIsExclusive() {
        AchievementsView view = new AchievementsView(SaveData.defaults());
        assertFalse(view.isBackButton(RIGHT, 590));
        assertFalse(view.isBackButton(720, BOTTOM));
        assertTrue(view.isBackButton(RIGHT - 1, BOTTOM - 1));
    }

    @Test
    public void clickElsewhereReturnsFalse() {
        AchievementsView view = new AchievementsView(SaveData.defaults());
        assertFalse(view.isBackButton(0, 0));
        assertFalse(view.isBackButton(400, 300));
    }

    // ROADMAP item 10 follow-up: click-outside-dismiss, mirroring
    // HamburgerMenu/PauseView's own miss-click convention -- this panel's own
    // layout is unchanged (PANEL_X/Y/W/H=40/20/760/590), only this new
    // dismiss behavior was added.
    private static final int PANEL_LEFT = 40, PANEL_TOP = 20;
    private static final int PANEL_RIGHT = 800, PANEL_BOTTOM = 610;

    @Test
    public void isInsidePanelReflectsTheUnchangedFullCanvasBounds() {
        AchievementsView view = new AchievementsView(SaveData.defaults());
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

        AchievementsView.showBlocking(handler, mouseInput, SaveData.defaults(), toast);
        clicker.join();
        // no exception / hang -- showBlocking returned, proving the miss-click resolved it
    }

    /**
     * The content-building step (visibleRows()) is exercised directly here,
     * separately from render()'s Graphics-dependent drawing, so hidden-
     * achievement omission and unlock-state wiring are unit-testable without
     * a headless Graphics context.
     */
    @Test
    public void hiddenAchievementIsOmittedUnlessUnlocked() {
        SaveData locked = SaveData.defaults();
        assertFalse(containsAchievement(new AchievementsView(locked).visibleRows(), Achievement.BAPI_EASTER_EGG));

        SaveData unlocked = SaveData.defaults();
        unlocked.unlock(Achievement.BAPI_EASTER_EGG);
        assertTrue(containsAchievement(new AchievementsView(unlocked).visibleRows(), Achievement.BAPI_EASTER_EGG));
    }

    @Test
    public void everyNonHiddenAchievementIsAlwaysVisible() {
        AchievementsView view = new AchievementsView(SaveData.defaults());
        for (Achievement achievement : Achievement.values()) {
            if (!achievement.isHidden()) {
                assertTrue(achievement + " must always be listed", containsAchievement(view.visibleRows(), achievement));
            }
        }
    }

    @Test
    public void visibleRowsReflectsLockedVsUnlockedState() {
        SaveData data = SaveData.defaults();
        data.unlock(Achievement.FIRST_VICTORY);
        AchievementsView view = new AchievementsView(data);

        for (AchievementsView.Row row : view.visibleRows()) {
            if (row.achievement == Achievement.FIRST_VICTORY) {
                assertTrue(row.unlocked);
            } else {
                assertFalse(row.achievement + " should still be locked", row.unlocked);
            }
        }
    }

    private static boolean containsAchievement(java.util.List<AchievementsView.Row> rows, Achievement achievement) {
        for (AchievementsView.Row row : rows) {
            if (row.achievement == achievement) {
                return true;
            }
        }
        return false;
    }

    /**
     * Code-review Finding 1 (achievement-toast click-to-dismiss): same wiring
     * check as TestRulesView.showBlockingDismissesToastHotspotClickBeforeCheckingBackButton,
     * against this class's own showBlocking overload -- see that test's doc
     * for the full rationale (AchievementToast's own predicates are already
     * covered by TestAchievementToast; this only smoke-tests the wiring).
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
            deliverClick(mouseInput, 720, 590); // the real Back button
        });
        clicker.start();

        AchievementsView.showBlocking(handler, mouseInput, SaveData.defaults(), toast);
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
