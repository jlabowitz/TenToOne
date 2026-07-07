import org.junit.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for NextTrickPrompt.isRulesHotspot, added by ROADMAP item 1 (no test
 * file previously existed for this class -- it had no testable logic before
 * this hotspot was added). Layout contract: the hotspot covers x in
 * [760, 820), y in [265, 295), same half-open-rect convention as
 * BetStepper.controlAt. This geometry is a design-time estimate, not yet
 * pixel-verified live against the running game -- see this item's
 * completion report.
 */
public class TestNextTrickPrompt {
    private static final int TOP = 265;
    private static final int BOTTOM = 295;
    private static final int LEFT = 760;
    private static final int RIGHT = 820;

    @Test
    public void clickInsideHotspotReturnsTrue() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertTrue(prompt.isRulesHotspot(790, 280));
    }

    @Test
    public void leftAndTopBoundaryIsInclusive() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertTrue(prompt.isRulesHotspot(LEFT, TOP));
    }

    @Test
    public void rightAndBottomBoundaryIsExclusive() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isRulesHotspot(RIGHT, 280));
        assertFalse(prompt.isRulesHotspot(790, BOTTOM));
        // last in-bounds pixel still hits
        assertTrue(prompt.isRulesHotspot(RIGHT - 1, BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowHotspotReturnsFalse() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isRulesHotspot(790, TOP - 1));
        assertFalse(prompt.isRulesHotspot(790, BOTTOM));
    }

    @Test
    public void clickLeftOrRightOfHotspotReturnsFalse() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isRulesHotspot(LEFT - 1, 280));
        assertFalse(prompt.isRulesHotspot(RIGHT, 280));
    }

    @Test
    public void clickOnMainPromptTextAreaReturnsFalse() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        // center of the canvas, where the centered click-to-continue text sits
        assertFalse(prompt.isRulesHotspot(420, 280));
    }

    // ROADMAP follow-up: in-game Achievements hotspot, same y band as Rules,
    // sitting to its left with a visible gap -- see NextTrickPrompt's
    // ACHIEVEMENTS_* fields comment for how this geometry was derived.
    private static final int ACHIEVEMENTS_TOP = 265;
    private static final int ACHIEVEMENTS_BOTTOM = 295;
    private static final int ACHIEVEMENTS_LEFT = 600;
    private static final int ACHIEVEMENTS_RIGHT = 730;

    @Test
    public void clickInsideAchievementsHotspotReturnsTrue() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertTrue(prompt.isAchievementsHotspot(665, 280));
    }

    @Test
    public void achievementsHotspotLeftAndTopBoundaryIsInclusive() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertTrue(prompt.isAchievementsHotspot(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP));
    }

    @Test
    public void achievementsHotspotRightAndBottomBoundaryIsExclusive() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isAchievementsHotspot(ACHIEVEMENTS_RIGHT, 280));
        assertFalse(prompt.isAchievementsHotspot(665, ACHIEVEMENTS_BOTTOM));
        assertTrue(prompt.isAchievementsHotspot(ACHIEVEMENTS_RIGHT - 1, ACHIEVEMENTS_BOTTOM - 1));
    }

    @Test
    public void clickOutsideAchievementsHotspotReturnsFalse() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isAchievementsHotspot(ACHIEVEMENTS_LEFT - 1, 280));
        assertFalse(prompt.isAchievementsHotspot(665, ACHIEVEMENTS_TOP - 1));
    }

    @Test
    public void clickOnMainPromptTextAreaReturnsFalseForAchievementsHotspotToo() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isAchievementsHotspot(420, 280));
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
                LEFT, TOP, RIGHT, BOTTOM));
    }

    /**
     * The centered "Click anywhere..." prompt text (rendered at the same
     * baseline y=280 this hotspot's y-band occupies) must not run under the
     * Achievements box -- measured via headless FontMetrics, not eyeballed.
     */
    @Test
    public void achievementsHotspotDoesNotOverlapPromptText() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        g.setFont(g.getFont().deriveFont(Font.BOLD));
        FontMetrics metrics = g.getFontMetrics();

        String text = "Click anywhere to move on to the next trick.";
        int width = metrics.stringWidth(text);
        int textEnd = (840 - width) / 2 + width; // Game.WIDTH = 840
        assertTrue("prompt text (ends at x=" + textEnd + ") must clear the Achievements box (starts at x=" + ACHIEVEMENTS_LEFT + ")",
                textEnd <= ACHIEVEMENTS_LEFT);
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
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertTrue(prompt.isHamburgerHotspot(20, 20));
    }

    @Test
    public void hamburgerHotspotLeftAndTopBoundaryIsInclusive() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertTrue(prompt.isHamburgerHotspot(HAMBURGER_LEFT, HAMBURGER_TOP));
    }

    @Test
    public void hamburgerHotspotRightAndBottomBoundaryIsExclusive() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isHamburgerHotspot(HAMBURGER_RIGHT, 20));
        assertFalse(prompt.isHamburgerHotspot(20, HAMBURGER_BOTTOM));
        assertTrue(prompt.isHamburgerHotspot(HAMBURGER_RIGHT - 1, HAMBURGER_BOTTOM - 1));
    }

    @Test
    public void clickOutsideHamburgerHotspotReturnsFalse() {
        NextTrickPrompt prompt = new NextTrickPrompt();
        assertFalse(prompt.isHamburgerHotspot(HAMBURGER_LEFT - 1, 20));
        assertFalse(prompt.isHamburgerHotspot(20, HAMBURGER_TOP - 1));
    }

    /**
     * Explicit non-collision proof: the hamburger hotspot must not intersect
     * the Rules box or the Achievements box -- this codebase has a
     * documented history of exactly this kind of overlay-button pixel
     * collision bug (see ROADMAP.md).
     */
    @Test
    public void hamburgerHotspotDoesNotOverlapRulesOrAchievements() {
        assertFalse(rectsOverlap(HAMBURGER_LEFT, HAMBURGER_TOP, HAMBURGER_RIGHT, HAMBURGER_BOTTOM,
                LEFT, TOP, RIGHT, BOTTOM));
        assertFalse(rectsOverlap(HAMBURGER_LEFT, HAMBURGER_TOP, HAMBURGER_RIGHT, HAMBURGER_BOTTOM,
                ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT, ACHIEVEMENTS_BOTTOM));
    }
}
