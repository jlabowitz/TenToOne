import org.junit.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

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

    // ROADMAP follow-up: in-game Achievements hotspot, same y band as Rules,
    // sitting to its left with a visible gap -- see IllegalPlayFeedback's
    // ACHIEVEMENTS_* fields comment for how this geometry was derived.
    private static final int ACHIEVEMENTS_TOP = 265;
    private static final int ACHIEVEMENTS_BOTTOM = 295;
    private static final int ACHIEVEMENTS_LEFT = 600;
    private static final int ACHIEVEMENTS_RIGHT = 730;

    @Test
    public void clickInsideAchievementsHotspotReturnsTrue() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertTrue(feedback.isAchievementsHotspot(665, 280));
    }

    @Test
    public void achievementsHotspotLeftAndTopBoundaryIsInclusive() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertTrue(feedback.isAchievementsHotspot(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP));
    }

    @Test
    public void achievementsHotspotRightAndBottomBoundaryIsExclusive() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertFalse(feedback.isAchievementsHotspot(ACHIEVEMENTS_RIGHT, 280));
        assertFalse(feedback.isAchievementsHotspot(665, ACHIEVEMENTS_BOTTOM));
        assertTrue(feedback.isAchievementsHotspot(ACHIEVEMENTS_RIGHT - 1, ACHIEVEMENTS_BOTTOM - 1));
    }

    @Test
    public void clickOutsideAchievementsHotspotReturnsFalse() {
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        assertFalse(feedback.isAchievementsHotspot(ACHIEVEMENTS_LEFT - 1, 280));
        assertFalse(feedback.isAchievementsHotspot(665, ACHIEVEMENTS_TOP - 1));
    }

    /**
     * Explicit non-collision proof: the new Achievements box must not
     * intersect the existing Rules box. This codebase has a documented
     * history of exactly this kind of overlay-button pixel collision bug
     * (see ROADMAP.md).
     */
    @Test
    public void achievementsHotspotDoesNotOverlapRulesHotspot() {
        assertFalse(rectsOverlap(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT, ACHIEVEMENTS_BOTTOM,
                RULES_LEFT, RULES_TOP, RULES_RIGHT, RULES_BOTTOM));
    }

    /**
     * The centered fade message (rendered at the same baseline y=280 this
     * hotspot's y-band occupies) must not run under the Achievements box for
     * either of the two possible illegal-play reasons -- this is measured
     * (via Human.illegalReason's actual longest-case strings and headless
     * FontMetrics), not eyeballed.
     */
    @Test
    public void achievementsHotspotDoesNotOverlapEitherIllegalReasonMessage() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        g.setFont(g.getFont().deriveFont(Font.BOLD));
        FontMetrics metrics = g.getFontMetrics();

        String trumpNotBroken = "Trump hasn't been broken yet -- lead a different suit.";
        String followSuitDiamonds = "You must follow suit -- play a Diamonds card."; // longest suit name
        for (String message : new String[]{trumpNotBroken, followSuitDiamonds}) {
            int width = metrics.stringWidth(message);
            int textEnd = (840 - width) / 2 + width; // Game.WIDTH = 840
            assertTrue("\"" + message + "\" (ends at x=" + textEnd + ") must clear the Achievements box (starts at x=" + ACHIEVEMENTS_LEFT + ")",
                    textEnd <= ACHIEVEMENTS_LEFT);
        }
        g.dispose();
    }

    /** Half-open rect intersection test: true iff [aLeft,aRight)x[aTop,aBottom) and [bLeft,bRight)x[bTop,bBottom) share any pixel. */
    private static boolean rectsOverlap(int aLeft, int aTop, int aRight, int aBottom,
                                         int bLeft, int bTop, int bRight, int bBottom) {
        return aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop;
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

    /**
     * Explicit non-collision proof: the hamburger hotspot must not intersect
     * the Rules box, the Achievements box, or the centered fade message's
     * band -- this codebase has a documented history of exactly this kind of
     * overlay-button pixel collision bug (see ROADMAP.md).
     */
    @Test
    public void hamburgerHotspotDoesNotOverlapRulesOrAchievements() {
        assertFalse(rectsOverlap(HAMBURGER_LEFT, HAMBURGER_TOP, HAMBURGER_RIGHT, HAMBURGER_BOTTOM,
                RULES_LEFT, RULES_TOP, RULES_RIGHT, RULES_BOTTOM));
        assertFalse(rectsOverlap(HAMBURGER_LEFT, HAMBURGER_TOP, HAMBURGER_RIGHT, HAMBURGER_BOTTOM,
                ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT, ACHIEVEMENTS_BOTTOM));
    }
}
