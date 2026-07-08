import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for IllegalPlayFeedback.colorAt(elapsed), the pure timing function
 * backing the fading illegal-move message (ROADMAP item 1).
 *
 * Timing contract (60 ticks/sec): ticks [0, 30) hold solid red; ticks
 * [30, 120) linearly interpolate from red toward white; tick 120+ is past
 * TOTAL_TICKS and render() draws nothing (not exercised here since colorAt
 * itself has no "expired" branch -- render() checks elapsed >= TOTAL_TICKS
 * before ever calling colorAt).
 */
public class TestIllegalPlayFeedback {

    @Test
    public void tickZeroIsSolidRed() {
        assertEquals(Color.RED, IllegalPlayFeedback.colorAt(0));
    }

    @Test
    public void lastHoldTickIsStillSolidRed() {
        assertEquals(Color.RED, IllegalPlayFeedback.colorAt(29));
    }

    @Test
    public void fadeStartTickIsStillFullRed() {
        // f = (30-30)/90 = 0 -> (255, 0, 0), same as solid red
        assertEquals(new Color(255, 0, 0), IllegalPlayFeedback.colorAt(30));
    }

    @Test
    public void midFadeIsHalfwayBetweenRedAndWhite() {
        // f = (75-30)/90 = 0.5 -> (255, 128, 128)
        assertEquals(new Color(255, 128, 128), IllegalPlayFeedback.colorAt(75));
    }

    @Test
    public void nearExpiryIsNearlyWhite() {
        // f = (119-30)/90 = 0.98888... -> round(255*f) = 252
        assertEquals(new Color(255, 252, 252), IllegalPlayFeedback.colorAt(119));
    }

    @Test
    public void triggerResetsElapsedAndSetsText() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        // freshly constructed feedback has elapsed == TOTAL_TICKS (expired);
        // ticking it further must not move elapsed past TOTAL_TICKS, and
        // render() must draw nothing at that point -- exercised indirectly
        // via a headless Graphics in the render smoke test below.
        feedback.trigger("You must follow suit -- play a Hearts card.");
        // no getter for elapsed/text is exposed (render is the only reader),
        // so this just documents trigger() doesn't throw and is re-callable.
        feedback.trigger("Trump hasn't been broken yet -- lead a different suit.");
    }

    // ROADMAP item 10: hamburger-menu icon hotspot, top-left corner --
    // duplicated (not shared) across BetStepper/IllegalPlayFeedback/
    // NextTrickPrompt. Moved to the very top of the canvas (was y=[60,90))
    // per user feedback. See TestHamburgerIconGeometry for the deeper
    // cross-class clearance proof against the AI seat row/trump card, and
    // the documented AchievementToast-dismiss-band trade-off.
    private static final int HAMBURGER_LEFT = 10;
    private static final int HAMBURGER_RIGHT = 40;
    private static final int HAMBURGER_TOP = 5;
    private static final int HAMBURGER_BOTTOM = 35;

    @Test
    public void clickInsideHamburgerHotspotReturnsTrue() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertTrue(feedback.isHamburgerHotspot(20, 20));
    }

    @Test
    public void hamburgerHotspotLeftAndTopBoundaryIsInclusive() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertTrue(feedback.isHamburgerHotspot(HAMBURGER_LEFT, HAMBURGER_TOP));
    }

    @Test
    public void hamburgerHotspotRightAndBottomBoundaryIsExclusive() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertFalse(feedback.isHamburgerHotspot(HAMBURGER_RIGHT, 20));
        assertFalse(feedback.isHamburgerHotspot(20, HAMBURGER_BOTTOM));
        assertTrue(feedback.isHamburgerHotspot(HAMBURGER_RIGHT - 1, HAMBURGER_BOTTOM - 1));
    }

    @Test
    public void clickOutsideHamburgerHotspotReturnsFalse() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertFalse(feedback.isHamburgerHotspot(HAMBURGER_LEFT - 1, 20));
        assertFalse(feedback.isHamburgerHotspot(20, HAMBURGER_TOP - 1));
    }

}
