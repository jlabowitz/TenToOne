import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for NextTrickPrompt's hamburger-menu icon hotspot. ROADMAP item 10
 * follow-up: this class's own dedicated Rules/Achievements hotspots (this
 * file used to test isRulesHotspot/isAchievementsHotspot) were removed --
 * Rules/Achievements are reached only through the hamburger dropdown now
 * (HamburgerMenu).
 */
public class TestNextTrickPrompt {

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
}
