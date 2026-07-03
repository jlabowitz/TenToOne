import org.junit.Test;

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
}
