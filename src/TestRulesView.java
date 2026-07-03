import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for RulesView.isBackButton, the only interactive hit-test this class
 * exposes (ROADMAP item 1). Layout contract: Back covers x in [680, 760),
 * y in [576, 602).
 */
public class TestRulesView {
    private static final int TOP = 576;
    private static final int BOTTOM = 602;
    private static final int LEFT = 680;
    private static final int RIGHT = 760;

    @Test
    public void clickInsideBackReturnsTrue() {
        RulesView view = new RulesView();
        assertTrue(view.isBackButton(720, 590));
    }

    @Test
    public void leftAndTopBoundaryIsInclusive() {
        RulesView view = new RulesView();
        assertTrue(view.isBackButton(LEFT, TOP));
    }

    @Test
    public void rightAndBottomBoundaryIsExclusive() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(RIGHT, 590));
        assertFalse(view.isBackButton(720, BOTTOM));
        // last in-bounds pixel still hits
        assertTrue(view.isBackButton(RIGHT - 1, BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowButtonReturnsFalse() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(720, TOP - 1));
        assertFalse(view.isBackButton(720, BOTTOM));
    }

    @Test
    public void clickLeftOrRightOfButtonReturnsFalse() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(LEFT - 1, 590));
        assertFalse(view.isBackButton(RIGHT, 590));
    }

    @Test
    public void clickElsewhereOnCanvasReturnsFalse() {
        RulesView view = new RulesView();
        assertFalse(view.isBackButton(0, 0));
        assertFalse(view.isBackButton(400, 300));
    }
}
