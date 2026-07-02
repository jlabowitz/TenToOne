import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
