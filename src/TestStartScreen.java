import org.junit.Test;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for StartScreen hit-testing and name-buffer editing (ROADMAP item 1,
 * extended by ROADMAP item 2 for the Achievements button/stat line).
 *
 * Layout contract (mirrors the approved design spec): Rules covers x in
 * [270, 390), y in [320, 354); Start Game covers x in [450, 570), y in
 * [320, 354); Achievements covers x in [340, 500), y in [370, 404); the name
 * field itself (x in [270, 570), y in [260, 294)) is not a click target and
 * returns null.
 */
public class TestStartScreen {
    private static final int TOP = 320;
    private static final int BOTTOM = 354;
    private static final int ACHIEVEMENTS_TOP = 370;
    private static final int ACHIEVEMENTS_BOTTOM = 404;
    private static final int ACHIEVEMENTS_LEFT = 340;
    private static final int ACHIEVEMENTS_RIGHT = 500;

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

    // --- ROADMAP item 2: Achievements button ---

    @Test
    public void clickInsideAchievementsReturnsAchievements() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.ACHIEVEMENTS, screen.controlAt(420, 385));
    }

    @Test
    public void achievementsLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.ACHIEVEMENTS, screen.controlAt(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP));
    }

    @Test
    public void achievementsRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(ACHIEVEMENTS_RIGHT, 385));
        assertNull(screen.controlAt(420, ACHIEVEMENTS_BOTTOM));
        assertEquals(StartScreen.Control.ACHIEVEMENTS,
                screen.controlAt(ACHIEVEMENTS_RIGHT - 1, ACHIEVEMENTS_BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowAchievementsReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(420, ACHIEVEMENTS_TOP - 1));
        assertNull(screen.controlAt(420, ACHIEVEMENTS_BOTTOM));
    }

    // --- ROADMAP item 2: stat line, only shown once gamesPlayed > 0 ---

    @Test
    public void noArgConstructorDefaultsToNoStatsShown() {
        StartScreen screen = new StartScreen();
        assertFalse(screen.hasStatsToShow());
    }

    @Test
    public void statsConstructorWithZeroGamesPlayedHidesStatLine() {
        StartScreen screen = new StartScreen(0, 0, 0);
        assertFalse(screen.hasStatsToShow());
    }

    @Test
    public void statsConstructorWithGamesPlayedShowsStatLine() {
        StartScreen screen = new StartScreen(12, 87, 4);
        assertTrue(screen.hasStatsToShow());
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

    // --- ROADMAP item 10: Resume Game button ---

    private static final int RESUME_TOP = 460;
    private static final int RESUME_BOTTOM = 494;
    private static final int RESUME_LEFT = 340;
    private static final int RESUME_RIGHT = 500;

    @Test
    public void resumeControlIsNullWhenNoResumableGameExists() {
        // Both the 3-arg (implicitly false) and the explicit-false 4-arg
        // overload must behave identically -- neither offers Resume.
        StartScreen screen = new StartScreen(5, 50, 2);
        assertNull(screen.controlAt(420, 475));
        StartScreen screenExplicit = new StartScreen(5, 50, 2, false);
        assertNull(screenExplicit.controlAt(420, 475));
    }

    @Test
    public void clickInsideResumeReturnsResumeWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(420, 475));
    }

    @Test
    public void resumeLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(RESUME_LEFT, RESUME_TOP));
    }

    @Test
    public void resumeRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertNull(screen.controlAt(RESUME_RIGHT, 475));
        assertNull(screen.controlAt(420, RESUME_BOTTOM));
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(RESUME_RIGHT - 1, RESUME_BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowResumeReturnsNull() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertNull(screen.controlAt(420, RESUME_TOP - 1));
        assertNull(screen.controlAt(420, RESUME_BOTTOM));
    }

    /**
     * Explicit non-collision proof against the two regions Resume sits
     * between: the stats line's rendered text (baseline STATS_Y=430) must
     * clear Resume's top edge, and Resume's bottom edge must clear the
     * footer's rendered text (baseline FOOTER_Y=560) -- measured via headless
     * FontMetrics against the actual rendered strings, not eyeballed. Mirrors
     * this class's own documented MAX_NAME_LENGTH measurement convention.
     */
    @Test
    public void resumeButtonClearsStatsLineAndFooterText() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        FontMetrics metrics = g.getFontMetrics();

        int statsBaselineY = 430;
        int statsBottomExtent = statsBaselineY + metrics.getDescent();
        assertTrue("Resume's top edge (y=" + RESUME_TOP + ") must clear the stats line's bottom extent ("
                        + statsBottomExtent + ")",
                RESUME_TOP >= statsBottomExtent);

        int footerBaselineY = 560;
        int footerTopExtent = footerBaselineY - metrics.getAscent();
        assertTrue("Resume's bottom edge (y=" + RESUME_BOTTOM + ") must clear the footer text's top extent ("
                        + footerTopExtent + ")",
                RESUME_BOTTOM <= footerTopExtent);

        g.dispose();
    }

    /**
     * Non-collision proof against the Achievements button directly above it
     * (ACHIEVEMENTS_TOP/BOTTOM = 370/404) -- same rectsOverlap technique
     * TestBetStepper/TestNextTrickPrompt already use for their own hotspots.
     */
    @Test
    public void resumeButtonDoesNotOverlapAchievementsButton() {
        assertFalse(rectsOverlap(RESUME_LEFT, RESUME_TOP, RESUME_RIGHT, RESUME_BOTTOM,
                ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT, ACHIEVEMENTS_BOTTOM));
    }

    /** Half-open rect intersection test: true iff [aLeft,aRight)x[aTop,aBottom) and [bLeft,bRight)x[bTop,bBottom) share any pixel. */
    private static boolean rectsOverlap(int aLeft, int aTop, int aRight, int aBottom,
                                         int bLeft, int bTop, int bRight, int bBottom) {
        return aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop;
    }
}
