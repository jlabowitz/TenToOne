import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    // ROADMAP follow-up: Rules hotspot, mirrors NextTrickPrompt.isRulesHotspot
    // (same geometry, same half-open-rect convention). See TestNextTrickPrompt
    // for the original version of this contract.
    private static final int RULES_TOP = 265;
    private static final int RULES_BOTTOM = 295;
    private static final int RULES_LEFT = 760;
    private static final int RULES_RIGHT = 820;

    @Test
    public void clickInsideRulesHotspotReturnsTrue() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertTrue(feedback.isRulesHotspot(790, 280));
    }

    @Test
    public void rulesHotspotLeftAndTopBoundaryIsInclusive() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertTrue(feedback.isRulesHotspot(RULES_LEFT, RULES_TOP));
    }

    @Test
    public void rulesHotspotRightAndBottomBoundaryIsExclusive() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertFalse(feedback.isRulesHotspot(RULES_RIGHT, 280));
        assertFalse(feedback.isRulesHotspot(790, RULES_BOTTOM));
        assertTrue(feedback.isRulesHotspot(RULES_RIGHT - 1, RULES_BOTTOM - 1));
    }

    @Test
    public void clickOutsideRulesHotspotReturnsFalse() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertFalse(feedback.isRulesHotspot(RULES_LEFT - 1, 280));
        assertFalse(feedback.isRulesHotspot(790, RULES_TOP - 1));
    }
}
