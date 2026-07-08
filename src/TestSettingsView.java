import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 10: tests for SettingsView's checkbox toggle.
 *
 * ROADMAP item 10 follow-up: the toggle now flips
 * GameSettings.pendingTotalBetsCannotEqualTricks (a staged value), NOT the
 * live totalBetsCannotEqualTricks field gameplay actually reads -- Round.bet()
 * would otherwise pick up a mid-round Settings change for the very next
 * bettor in the same round, which the user flagged as confusing. Only
 * GameSettings.applyPending() (called by Game.java at the start of a
 * genuinely fresh game -- see that class's own doc) copies the staged value
 * onto the live one. These tests assert on the pending field directly; the
 * live-field gating itself is covered by TestGame's own
 * settingsToggleFromHamburgerMenuStagesOnTheSameGameSettingsInstanceGameUsesButDoesNotApplyMidRound.
 */
public class TestSettingsView {

    @Test(timeout = 5000)
    public void clickingToggleFlipsThePendingValueOnTheLiveInstanceNotTheEffectiveValue() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        GameSettings settings = new GameSettings();
        assertTrue("default is ON per GameSettings' own doc", settings.pendingTotalBetsCannotEqualTricks);

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 550, 135); // the toggle
            deliverClick(mouseInput, 700, 310); // Back (ROADMAP item 10 follow-up: moved up from y=590 as part of shrinking PANEL_H 590 -> 314)
        });
        clicker.start();

        SettingsView.showBlocking(handler, mouseInput, toast, settings);
        clicker.join();

        assertFalse("one click must flip the pending toggle off", settings.pendingTotalBetsCannotEqualTricks);
        assertTrue("the effective (live) value must NOT change just from visiting Settings -- "
                        + "only applyPending() (called at the start of a fresh game) does that",
                settings.totalBetsCannotEqualTricks);
    }

    @Test(timeout = 5000)
    public void clickingToggleTwiceReturnsThePendingValueToOriginalState() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        GameSettings settings = new GameSettings();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 550, 135); // toggle off
            deliverClick(mouseInput, 550, 135); // toggle back on
            deliverClick(mouseInput, 700, 310); // Back (ROADMAP item 10 follow-up: moved up from y=590 as part of shrinking PANEL_H 590 -> 314)
        });
        clicker.start();

        SettingsView.showBlocking(handler, mouseInput, toast, settings);
        clicker.join();

        assertTrue(settings.pendingTotalBetsCannotEqualTricks);
    }

    // --- ROADMAP item 10 follow-up: click-outside-dismiss, via the shared
    // ModalDismiss helper. Panel bounds: 40,20,760,314 (H trimmed from 590 in
    // this same pass; see SettingsView's own doc).

    private static final int PANEL_LEFT = 40, PANEL_TOP = 20;
    private static final int PANEL_RIGHT = 800, PANEL_BOTTOM = 334;

    @Test
    public void isInsidePanelReflectsTheShrunkBounds() {
        SettingsView view = new SettingsView(new GameSettings());
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
        GameSettings settings = new GameSettings();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 820, 615); // well outside the panel
        });
        clicker.start();

        SettingsView.showBlocking(handler, mouseInput, toast, settings);
        clicker.join();
        // no exception / hang -- showBlocking returned, proving the miss-click resolved it
    }

    @Test
    public void applyPendingCopiesThePendingValueOntoTheEffectiveOne() {
        GameSettings settings = new GameSettings();
        settings.pendingTotalBetsCannotEqualTricks = false;
        assertTrue("must still be ON before applyPending() runs", settings.totalBetsCannotEqualTricks);

        settings.applyPending();

        assertFalse("applyPending() must copy the staged value onto the effective one",
                settings.totalBetsCannotEqualTricks);
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
