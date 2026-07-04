import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for AchievementToast (ROADMAP item 2): the queued (not
 * overwrite-in-place) mid-session unlock toast, backed by a FIFO queue
 * instead of a single slot, and gated behind activate() so items enqueued
 * during the Start Screen don't render/tick until real gameplay begins (see
 * class doc). Hold/fade timing (240 ticks solid hold + 120 ticks fade = 360
 * total) was lengthened from an original 30/90 shape borrowed from
 * IllegalPlayFeedback after user feedback that the shorter duration expired
 * before there was time to read it -- see class doc's "ROADMAP follow-up"
 * paragraph. Also covers isToastHotspot()/dismiss(), the click-to-dismiss
 * affordance added alongside the longer duration so a player who's already
 * read the message isn't stuck waiting out the full hold/fade.
 */
public class TestAchievementToast {

    @Test
    public void colorAtTickZeroIsSolidGold() {
        assertEquals(AchievementToast.GOLD, AchievementToast.colorAt(0));
    }

    @Test
    public void colorAtLastHoldTickIsStillSolidGold() {
        assertEquals(AchievementToast.GOLD, AchievementToast.colorAt(AchievementToast.HOLD_TICKS - 1));
    }

    @Test
    public void colorAtMidFadeIsHalfwayToWhite() {
        // elapsed = HOLD_TICKS + FADE_TICKS/2 = 240 + 60 = 300
        // f = (300-240)/120 = 0.5; GOLD is (204,153,0).
        // red:   204 + round(51*0.5)  = 204 + round(25.5) = 204 + 26 = 230
        // green: 153 + round(102*0.5) = 153 + round(51.0) = 153 + 51 = 204
        // blue:  0   + round(255*0.5) = 0   + round(127.5) = 0 + 128 = 128
        assertEquals(new Color(230, 204, 128),
                AchievementToast.colorAt(AchievementToast.HOLD_TICKS + AchievementToast.FADE_TICKS / 2));
    }

    /**
     * Before activate(), enqueued items must not tick/render -- this is the
     * exact seam that keeps a Bapi-triggered toast queued during the Start
     * Screen from being silently dropped or drawn underneath it (see class
     * doc). isShowingSomething() is a test-only introspection hook.
     */
    @Test
    public void queuedItemsDoNotStartUntilActivated() {
        AchievementToast toast = new AchievementToast();
        toast.enqueue("Achievement Unlocked: One and Only");

        for (int i = 0; i < 5; i++) {
            toast.tick();
        }

        assertFalse("must not start showing before activate()", toast.isShowingSomething());
    }

    @Test
    public void activateStartsShowingTheFirstQueuedItem() {
        AchievementToast toast = new AchievementToast();
        toast.enqueue("Achievement Unlocked: First Victory");
        toast.activate();

        toast.tick();

        assertTrue(toast.isShowingSomething());
    }

    /**
     * Two achievements unlocking in the same instant (e.g. a score
     * threshold and a round-bonus threshold both crossing on the same
     * round-end) must both display, one after another -- never silently
     * dropping the second in favor of overwrite-in-place.
     */
    @Test
    public void secondQueuedItemDisplaysAfterTheFirstExpires() {
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("First");
        toast.enqueue("Second");

        toast.tick(); // dequeues "First", elapsed reset to 0
        assertEquals("First", toast.getCurrentText());

        // run elapsed from 0 up to TOTAL_TICKS (one tick() per increment)...
        for (int i = 0; i < AchievementToast.TOTAL_TICKS; i++) {
            toast.tick();
        }
        // ...then one more tick() to actually observe elapsed >= TOTAL_TICKS
        // and dequeue "Second" -- tick() checks-then-increments, so reaching
        // the boundary value and acting on it are one tick apart, same as
        // IllegalPlayFeedback's own elapsed/TOTAL_TICKS convention.
        toast.tick();

        assertEquals("Second", toast.getCurrentText());
    }

    @Test
    public void withNothingQueuedIsShowingSomethingIsFalse() {
        AchievementToast toast = new AchievementToast();
        toast.activate();

        toast.tick();

        assertFalse(toast.isShowingSomething());
    }

    @Test
    public void isToastHotspotFalseWhenNothingIsShowing() {
        AchievementToast toast = new AchievementToast();
        toast.activate();

        assertFalse(toast.isToastHotspot(400, 20));
    }

    @Test
    public void isToastHotspotTrueWithinBandWhileShowing() {
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Achievement Unlocked: First Victory");
        toast.tick();

        assertTrue(toast.isToastHotspot(400, 20));
        // full canvas width is fair game, not just where the centered text happens to sit
        assertTrue(toast.isToastHotspot(0, 0));
        assertTrue(toast.isToastHotspot(Game.WIDTH - 1, AchievementToast.TOAST_BAND_BOTTOM - 1));
    }

    @Test
    public void isToastHotspotBottomBoundaryIsExclusive() {
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Achievement Unlocked: First Victory");
        toast.tick();

        assertFalse(toast.isToastHotspot(400, AchievementToast.TOAST_BAND_BOTTOM));
    }

    /**
     * Mid-hold click-dismiss: must clear both introspection hooks
     * immediately, not just let the timer run out on its own.
     */
    @Test
    public void dismissEndsDisplayImmediatelyMidHold() {
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Achievement Unlocked: First Victory");
        toast.tick(); // dequeues, elapsed reset to 0 -- solidly mid-hold
        assertTrue(toast.isShowingSomething());

        toast.dismiss();

        assertFalse(toast.isShowingSomething());
        assertNull(toast.getCurrentText());
        assertFalse("dismissed toast is no longer a click target", toast.isToastHotspot(400, 20));
    }

    /** Mid-fade click-dismiss: same immediate-clear contract as mid-hold. */
    @Test
    public void dismissEndsDisplayImmediatelyMidFade() {
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Achievement Unlocked: First Victory");
        toast.tick(); // dequeues, elapsed reset to 0
        for (int i = 0; i < AchievementToast.HOLD_TICKS + 5; i++) {
            toast.tick(); // now mid-fade
        }
        assertTrue(toast.isShowingSomething());

        toast.dismiss();

        assertFalse(toast.isShowingSomething());
        assertNull(toast.getCurrentText());
    }

    /**
     * A dismissed toast must not get stuck -- the queue advances to the next
     * item on the very next tick(), same as an ordinary expiry.
     */
    @Test
    public void dismissedToastAdvancesToNextQueuedItemOnNextTick() {
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("First");
        toast.enqueue("Second");
        toast.tick(); // dequeues "First"
        assertEquals("First", toast.getCurrentText());

        toast.dismiss();
        toast.tick(); // check-then-increment: this is the tick that dequeues "Second"

        assertEquals("Second", toast.getCurrentText());
    }
}
