import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for StatsView (ROADMAP item 10 follow-up): the new Stats modal
 * reachable from the Start Screen's Stats button. Mirrors
 * TestSettingsView/TestAchievementsView's own showBlocking/hit-test/
 * outside-dismiss test shape.
 */
public class TestStatsView {
    private static final int BACK_TOP = 344, BACK_BOTTOM = 370;
    private static final int BACK_LEFT = 680, BACK_RIGHT = 760;

    private static final int PANEL_LEFT = 40, PANEL_TOP = 20;
    private static final int PANEL_RIGHT = 800, PANEL_BOTTOM = 378;

    @Test
    public void clickInsideBackReturnsTrue() {
        StatsView view = new StatsView(SaveData.defaults());
        assertTrue(view.isBackButton(720, 355));
    }

    @Test
    public void leftAndTopBoundaryIsInclusive() {
        StatsView view = new StatsView(SaveData.defaults());
        assertTrue(view.isBackButton(BACK_LEFT, BACK_TOP));
    }

    @Test
    public void rightAndBottomBoundaryIsExclusive() {
        StatsView view = new StatsView(SaveData.defaults());
        assertFalse(view.isBackButton(BACK_RIGHT, 355));
        assertFalse(view.isBackButton(720, BACK_BOTTOM));
        assertTrue(view.isBackButton(BACK_RIGHT - 1, BACK_BOTTOM - 1));
    }

    @Test
    public void clickElsewhereReturnsFalse() {
        StatsView view = new StatsView(SaveData.defaults());
        assertFalse(view.isBackButton(0, 0));
        assertFalse(view.isBackButton(400, 200));
    }

    @Test
    public void isInsidePanelReflectsTheShrunkBounds() {
        StatsView view = new StatsView(SaveData.defaults());
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
            sleep50();
            deliverClick(mouseInput, 820, 600); // well outside the panel
        });
        clicker.start();

        StatsView.showBlocking(handler, mouseInput, toast, SaveData.defaults());
        clicker.join();
        // no exception / hang -- showBlocking returned, proving the miss-click resolved it
    }

    @Test(timeout = 5000)
    public void backButtonResolvesTheBlockingCall() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 720, 355); // Back
        });
        clicker.start();

        StatsView.showBlocking(handler, mouseInput, toast, SaveData.defaults());
        clicker.join();
    }

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
            sleep50();
            deliverClick(mouseInput, 400, 20); // inside the toast's dismiss band, clear of Back
            deliverClick(mouseInput, 720, 355); // the real Back button
        });
        clicker.start();

        StatsView.showBlocking(handler, mouseInput, toast, SaveData.defaults());
        clicker.join();

        assertFalse("the first click (inside the toast band) must dismiss the toast rather than being ignored",
                toast.isShowingSomething());
    }

    // --- pure derived-stat functions: divide-by-zero guards + basic math ---

    @Test
    public void winPercentIsZeroWhenNoGamesPlayed() {
        SaveData data = SaveData.defaults();
        assertEquals(0, StatsView.winPercent(data));
    }

    @Test
    public void winPercentComputesWholeNumberPercentage() {
        SaveData data = SaveData.defaults();
        data.gamesPlayed = 4;
        data.gamesWon = 3;
        assertEquals(75, StatsView.winPercent(data));
    }

    @Test
    public void averageScorePerGameIsZeroWhenNoGamesPlayed() {
        SaveData data = SaveData.defaults();
        assertEquals(0.0, StatsView.averageScorePerGame(data), 0.0001);
    }

    @Test
    public void averageScorePerGameDividesTotalPointsByGamesPlayed() {
        SaveData data = SaveData.defaults();
        data.gamesPlayed = 4;
        data.totalPoints = 100;
        assertEquals(25.0, StatsView.averageScorePerGame(data), 0.0001);
    }

    @Test
    public void betHitPercentIsZeroWhenNoRoundsBetIn() {
        SaveData data = SaveData.defaults();
        assertEquals(0, StatsView.betHitPercent(data));
    }

    @Test
    public void betHitPercentComputesWholeNumberPercentage() {
        SaveData data = SaveData.defaults();
        data.totalRoundsBet = 10;
        data.totalRoundsBetHit = 4;
        assertEquals(40, StatsView.betHitPercent(data));
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
