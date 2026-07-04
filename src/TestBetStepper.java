import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for BetStepper hit-testing and draft-value clamping.
 *
 * Layout contract (mirrors the approved design spec): all controls share
 * y in [585, 619). Decrement covers x in [620, 650), the value display
 * covers x in [655, 695) (not clickable), increment covers x in [700, 730),
 * and Bet covers x in [740, 800). Gaps between controls (e.g. x in
 * [650, 655)) are dead zones.
 */
public class TestBetStepper {
    private static final int TOP = 585;
    private static final int BOTTOM = 619;

    @Test
    public void initialValueIsZero() {
        BetStepper stepper = new BetStepper(10);
        assertEquals(0, stepper.getValue());
    }

    @Test
    public void clickInsideDecrementReturnsDecrement() {
        BetStepper stepper = new BetStepper(10);
        assertEquals(BetStepper.Control.DECREMENT, stepper.controlAt(635, 600));
    }

    @Test
    public void decrementLeftAndTopBoundaryIsInclusive() {
        BetStepper stepper = new BetStepper(10);
        assertEquals(BetStepper.Control.DECREMENT, stepper.controlAt(620, TOP));
    }

    @Test
    public void decrementRightAndBottomBoundaryIsExclusive() {
        BetStepper stepper = new BetStepper(10);
        assertNull(stepper.controlAt(650, 600));
        assertNull(stepper.controlAt(635, BOTTOM));
        // last in-bounds pixel still hits
        assertEquals(BetStepper.Control.DECREMENT, stepper.controlAt(649, BOTTOM - 1));
    }

    @Test
    public void clickInsideIncrementReturnsIncrement() {
        BetStepper stepper = new BetStepper(10);
        assertEquals(BetStepper.Control.INCREMENT, stepper.controlAt(715, 600));
    }

    @Test
    public void incrementLeftAndTopBoundaryIsInclusive() {
        BetStepper stepper = new BetStepper(10);
        assertEquals(BetStepper.Control.INCREMENT, stepper.controlAt(700, TOP));
    }

    @Test
    public void incrementRightAndBottomBoundaryIsExclusive() {
        BetStepper stepper = new BetStepper(10);
        assertNull(stepper.controlAt(730, 600));
        assertNull(stepper.controlAt(715, BOTTOM));
        assertEquals(BetStepper.Control.INCREMENT, stepper.controlAt(729, BOTTOM - 1));
    }

    @Test
    public void clickInsideBetReturnsBet() {
        BetStepper stepper = new BetStepper(10);
        assertEquals(BetStepper.Control.BET, stepper.controlAt(770, 600));
    }

    @Test
    public void betLeftAndTopBoundaryIsInclusive() {
        BetStepper stepper = new BetStepper(10);
        assertEquals(BetStepper.Control.BET, stepper.controlAt(740, TOP));
    }

    @Test
    public void betRightAndBottomBoundaryIsExclusive() {
        BetStepper stepper = new BetStepper(10);
        assertNull(stepper.controlAt(800, 600));
        assertNull(stepper.controlAt(770, BOTTOM));
        assertEquals(BetStepper.Control.BET, stepper.controlAt(799, BOTTOM - 1));
    }

    @Test
    public void clickInGapBetweenControlsReturnsNull() {
        BetStepper stepper = new BetStepper(10);
        // Dead zone between decrement [620,650) and value display [655,695)
        assertNull(stepper.controlAt(652, 600));
    }

    @Test
    public void clickOnValueDisplayReturnsNull() {
        BetStepper stepper = new BetStepper(10);
        assertNull(stepper.controlAt(670, 600));
        // boundaries of the value display range too
        assertNull(stepper.controlAt(655, 600));
        assertNull(stepper.controlAt(694, 600));
    }

    @Test
    public void clickAboveOrBelowRowReturnsNull() {
        BetStepper stepper = new BetStepper(10);
        assertNull(stepper.controlAt(635, TOP - 1));
        assertNull(stepper.controlAt(635, BOTTOM));
        assertNull(stepper.controlAt(770, TOP - 1));
        assertNull(stepper.controlAt(770, BOTTOM));
    }

    @Test
    public void decrementAtZeroIsNoOp() {
        BetStepper stepper = new BetStepper(10);
        stepper.decrement();
        assertEquals(0, stepper.getValue());
    }

    @Test
    public void incrementAtMaxBetIsNoOp() {
        BetStepper stepper = new BetStepper(2);
        stepper.increment();
        stepper.increment();
        assertEquals(2, stepper.getValue());
        stepper.increment();
        assertEquals(2, stepper.getValue());
    }

    @Test
    public void sequenceOfIncrementsAndDecrementsLandsOnExpectedValue() {
        BetStepper stepper = new BetStepper(10);
        stepper.increment();
        stepper.increment();
        stepper.increment();
        assertEquals(3, stepper.getValue());
        stepper.decrement();
        stepper.decrement();
        stepper.decrement();
        assertEquals(0, stepper.getValue());
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
        BetStepper stepper = new BetStepper(10);
        assertTrue(stepper.isRulesHotspot(790, 280));
    }

    @Test
    public void rulesHotspotLeftAndTopBoundaryIsInclusive() {
        BetStepper stepper = new BetStepper(10);
        assertTrue(stepper.isRulesHotspot(RULES_LEFT, RULES_TOP));
    }

    @Test
    public void rulesHotspotRightAndBottomBoundaryIsExclusive() {
        BetStepper stepper = new BetStepper(10);
        assertFalse(stepper.isRulesHotspot(RULES_RIGHT, 280));
        assertFalse(stepper.isRulesHotspot(790, RULES_BOTTOM));
        assertTrue(stepper.isRulesHotspot(RULES_RIGHT - 1, RULES_BOTTOM - 1));
    }

    @Test
    public void clickOutsideRulesHotspotReturnsFalse() {
        BetStepper stepper = new BetStepper(10);
        assertFalse(stepper.isRulesHotspot(RULES_LEFT - 1, 280));
        assertFalse(stepper.isRulesHotspot(790, RULES_TOP - 1));
        // doesn't collide with the Bet control's own row
        assertFalse(stepper.isRulesHotspot(770, 600));
    }

    // ROADMAP follow-up: in-game Achievements hotspot, same y band as Rules,
    // sitting to its left with a visible gap -- see BetStepper's
    // ACHIEVEMENTS_* fields comment for how this geometry was derived.
    private static final int ACHIEVEMENTS_TOP = 265;
    private static final int ACHIEVEMENTS_BOTTOM = 295;
    private static final int ACHIEVEMENTS_LEFT = 600;
    private static final int ACHIEVEMENTS_RIGHT = 730;

    @Test
    public void clickInsideAchievementsHotspotReturnsTrue() {
        BetStepper stepper = new BetStepper(10);
        assertTrue(stepper.isAchievementsHotspot(665, 280));
    }

    @Test
    public void achievementsHotspotLeftAndTopBoundaryIsInclusive() {
        BetStepper stepper = new BetStepper(10);
        assertTrue(stepper.isAchievementsHotspot(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP));
    }

    @Test
    public void achievementsHotspotRightAndBottomBoundaryIsExclusive() {
        BetStepper stepper = new BetStepper(10);
        assertFalse(stepper.isAchievementsHotspot(ACHIEVEMENTS_RIGHT, 280));
        assertFalse(stepper.isAchievementsHotspot(665, ACHIEVEMENTS_BOTTOM));
        assertTrue(stepper.isAchievementsHotspot(ACHIEVEMENTS_RIGHT - 1, ACHIEVEMENTS_BOTTOM - 1));
    }

    @Test
    public void clickOutsideAchievementsHotspotReturnsFalse() {
        BetStepper stepper = new BetStepper(10);
        assertFalse(stepper.isAchievementsHotspot(ACHIEVEMENTS_LEFT - 1, 280));
        assertFalse(stepper.isAchievementsHotspot(665, ACHIEVEMENTS_TOP - 1));
        // doesn't collide with this class's own DECREMENT/VALUE/INCREMENT/BET row
        assertFalse(stepper.isAchievementsHotspot(665, 600));
    }

    /**
     * Explicit non-collision proof (not just "eyeballed clear"): the new
     * Achievements box must not intersect the existing Rules box, nor this
     * class's own DECREMENT-through-BET control row -- this codebase has a
     * documented history of exactly this kind of overlay-button pixel
     * collision bug (see ROADMAP.md).
     */
    @Test
    public void achievementsHotspotDoesNotOverlapRulesHotspotOrControlRow() {
        assertFalse("Achievements box must not overlap the Rules box",
                rectsOverlap(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT, ACHIEVEMENTS_BOTTOM,
                        RULES_LEFT, RULES_TOP, RULES_RIGHT, RULES_BOTTOM));
        // this class's own control row: DECREMENT_LEFT (620) through BET_RIGHT (800), y=[TOP,BOTTOM)=[585,619)
        assertFalse("Achievements box must not overlap this class's own control row",
                rectsOverlap(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT, ACHIEVEMENTS_BOTTOM,
                        620, TOP, 800, BOTTOM));
    }

    /** Half-open rect intersection test: true iff [aLeft,aRight)x[aTop,aBottom) and [bLeft,bRight)x[bTop,bBottom) share any pixel. */
    private static boolean rectsOverlap(int aLeft, int aTop, int aRight, int aBottom,
                                         int bLeft, int bTop, int bRight, int bBottom) {
        return aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop;
    }
}
