import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for StartScreen hit-testing and name-buffer editing (ROADMAP item 1).
 *
 * Layout contract (mirrors the approved design spec): Rules covers x in
 * [270, 390), y in [320, 354); Start Game covers x in [450, 570), y in
 * [320, 354); the name field itself (x in [270, 570), y in [260, 294)) is
 * not a click target and returns null.
 */
public class TestStartScreen {
    private static final int TOP = 320;
    private static final int BOTTOM = 354;

    @Test
    public void clickInsideRulesReturnsRules() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.RULES, screen.controlAt(300, 335));
    }

    @Test
    public void rulesLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.RULES, screen.controlAt(270, TOP));
    }

    @Test
    public void rulesRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(390, 335));
        assertNull(screen.controlAt(300, BOTTOM));
        // last in-bounds pixel still hits
        assertEquals(StartScreen.Control.RULES, screen.controlAt(389, BOTTOM - 1));
    }

    @Test
    public void clickInsideStartReturnsStart() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.START, screen.controlAt(500, 335));
    }

    @Test
    public void startLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.START, screen.controlAt(450, TOP));
    }

    @Test
    public void startRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(570, 335));
        assertNull(screen.controlAt(500, BOTTOM));
        assertEquals(StartScreen.Control.START, screen.controlAt(569, BOTTOM - 1));
    }

    @Test
    public void clickInGapBetweenButtonsReturnsNull() {
        StartScreen screen = new StartScreen();
        // Dead zone between Rules [270,390) and Start [450,570)
        assertNull(screen.controlAt(420, 335));
    }

    @Test
    public void clickAboveOrBelowButtonRowReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(300, TOP - 1));
        assertNull(screen.controlAt(300, BOTTOM));
        assertNull(screen.controlAt(500, TOP - 1));
        assertNull(screen.controlAt(500, BOTTOM));
    }

    @Test
    public void clickOnNameFieldReturnsNull() {
        StartScreen screen = new StartScreen();
        // field spans x in [270,570), y in [260,294) -- overlaps the Rules/
        // Start buttons' x-span but not their y-span, so this must miss both.
        assertNull(screen.controlAt(300, 275));
        assertNull(screen.controlAt(270, 260));
        assertNull(screen.controlAt(569, 293));
    }

    @Test
    public void clickElsewhereOnCanvasReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(0, 0));
        assertNull(screen.controlAt(420, 500));
    }

    @Test
    public void nameStartsEmpty() {
        StartScreen screen = new StartScreen();
        assertEquals("", screen.getName());
    }

    @Test
    public void typeCharAppendsToName() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('l');
        screen.typeChar('e');
        screen.typeChar('x');
        assertEquals("Alex", screen.getName());
    }

    @Test
    public void backspaceTrimsLastChar() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('l');
        screen.backspace();
        assertEquals("A", screen.getName());
    }

    @Test
    public void backspaceOnEmptyNameIsNoOp() {
        StartScreen screen = new StartScreen();
        screen.backspace();
        assertEquals("", screen.getName());
    }

    @Test
    public void typeCharClampsAtMaxNameLength() {
        StartScreen screen = new StartScreen();
        for (int i = 0; i < StartScreen.MAX_NAME_LENGTH; i++) {
            screen.typeChar('W');
        }
        assertEquals(StartScreen.MAX_NAME_LENGTH, screen.getName().length());
        // one more char past the cap must be dropped, not appended
        screen.typeChar('W');
        assertEquals(StartScreen.MAX_NAME_LENGTH, screen.getName().length());
    }

    @Test
    public void typeCharAtOneBelowMaxStillAppends() {
        StartScreen screen = new StartScreen();
        for (int i = 0; i < StartScreen.MAX_NAME_LENGTH - 1; i++) {
            screen.typeChar('W');
        }
        assertEquals(StartScreen.MAX_NAME_LENGTH - 1, screen.getName().length());
        screen.typeChar('W');
        assertEquals(StartScreen.MAX_NAME_LENGTH, screen.getName().length());
    }

    /**
     * Locks in the measured value (see this item's completion report for the
     * FontMetrics measurement this was derived from) so a future change to
     * RoundSummaryPanel's NAME_X/BET_X column doesn't silently invalidate
     * this constant without a test noticing.
     */
    @Test
    public void maxNameLengthIsThirteen() {
        assertEquals(13, StartScreen.MAX_NAME_LENGTH);
    }

    @Test
    public void backspaceAfterHittingCapAllowsTypingAgain() {
        StartScreen screen = new StartScreen();
        for (int i = 0; i < StartScreen.MAX_NAME_LENGTH; i++) {
            screen.typeChar('W');
        }
        screen.backspace();
        assertEquals(StartScreen.MAX_NAME_LENGTH - 1, screen.getName().length());
        screen.typeChar('Q');
        assertEquals(StartScreen.MAX_NAME_LENGTH, screen.getName().length());
        assertTrue(screen.getName().endsWith("Q"));
    }
}
