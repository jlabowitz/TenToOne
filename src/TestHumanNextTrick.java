import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Wiring test for Human.nextTrick()'s click loop (coverage gap noted in
 * QA's follow-up on the achievement-toast click-to-dismiss wiring; see
 * TestHumanPlayCard's class doc for the full rationale and
 * TestRulesView.showBlockingDismissesToastHotspotClickBeforeCheckingBackButton
 * for the pattern this mirrors). QA manually verified only Human.bet()'s
 * dismiss live and confirmed the guard is byte-for-byte identical at all
 * three Human sites; this locks nextTrick()'s copy in with an automated
 * test too.
 */
public class TestHumanNextTrick {

    @Test(timeout = 5000)
    public void nextTrickDismissesToastHotspotClickBeforeReturning() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Test Achievement");
        toast.tick();
        assertTrue("toast must actually be showing for isToastHotspot() to fire", toast.isShowingSomething());

        Human human = new Human("Test", mouseInput, handler, toast, SaveData.defaults());

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 400, 20); // inside the toast's dismiss band (y < 50), clear of everything else
            deliverClick(mouseInput, 400, 400); // any other click -- nextTrick() returns on the first non-hotspot click
        });
        clicker.start();

        human.nextTrick();
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
