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
 * extended by ROADMAP item 2 for the Achievements button/stat line, and by
 * ROADMAP item 10's follow-up polish pass for the three-row Resume/Start,
 * Rules/Achievements, Settings layout below).
 *
 * Layout contract (three rows, mirrors StartScreen's own class doc):
 *
 * Row 1 (y=[320,354)): "Resume Game" at x=[270,390) + "Start Game" at
 * x=[450,570) when hasResumableGame is true; otherwise "Start Game" alone,
 * centered at x=[340,500).
 *
 * Row 2 (y=[370,404)), always both regardless of hasResumableGame: "Rules"
 * at x=[270,390) + "Achievements" at x=[410,570).
 *
 * Row 3 (y=[460,494)), always: "Settings" at x=[340,500).
 */
public class TestStartScreen {
    private static final int ROW1_TOP = 320, ROW1_BOTTOM = 354;
    private static final int ROW2_TOP = 370, ROW2_BOTTOM = 404;
    private static final int ROW3_TOP = 460, ROW3_BOTTOM = 494;

    private static final int RESUME_LEFT = 270, RESUME_RIGHT = 390;
    private static final int START_WITH_RESUME_LEFT = 450, START_WITH_RESUME_RIGHT = 570;
    private static final int START_ALONE_LEFT = 340, START_ALONE_RIGHT = 500;

    private static final int RULES_LEFT = 270, RULES_RIGHT = 390;
    private static final int ACHIEVEMENTS_LEFT = 410, ACHIEVEMENTS_RIGHT = 570;

    private static final int SETTINGS_LEFT = 340, SETTINGS_RIGHT = 500;

    // --- Row 2: Rules (always present, regardless of hasResumableGame) ---

    @Test
    public void clickInsideRulesReturnsRulesWhenNoResumableGame() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.RULES, screen.controlAt(300, 385));
    }

    @Test
    public void clickInsideRulesReturnsRulesWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.RULES, screen.controlAt(300, 385));
    }

    @Test
    public void rulesLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.RULES, screen.controlAt(RULES_LEFT, ROW2_TOP));
    }

    @Test
    public void rulesRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(RULES_RIGHT, 385));
        assertNull(screen.controlAt(300, ROW2_BOTTOM));
        // last in-bounds pixel still hits
        assertEquals(StartScreen.Control.RULES, screen.controlAt(RULES_RIGHT - 1, ROW2_BOTTOM - 1));
    }

    // --- Row 2: Achievements (always present) ---

    @Test
    public void clickInsideAchievementsReturnsAchievements() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.ACHIEVEMENTS, screen.controlAt(480, 385));
    }

    @Test
    public void achievementsLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.ACHIEVEMENTS, screen.controlAt(ACHIEVEMENTS_LEFT, ROW2_TOP));
    }

    @Test
    public void achievementsRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(ACHIEVEMENTS_RIGHT, 385));
        assertNull(screen.controlAt(480, ROW2_BOTTOM));
        assertEquals(StartScreen.Control.ACHIEVEMENTS,
                screen.controlAt(ACHIEVEMENTS_RIGHT - 1, ROW2_BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowRow2ReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(300, ROW2_TOP - 1));
        assertNull(screen.controlAt(300, ROW2_BOTTOM));
        assertNull(screen.controlAt(480, ROW2_TOP - 1));
        assertNull(screen.controlAt(480, ROW2_BOTTOM));
    }

    @Test
    public void clickInGapBetweenRulesAndAchievementsReturnsNull() {
        StartScreen screen = new StartScreen();
        // Dead zone between Rules [270,390) and Achievements [410,570)
        assertNull(screen.controlAt(400, 385));
    }

    // --- Row 1: Start Game alone (no resumable game) ---

    @Test
    public void clickInsideStartAloneReturnsStartWhenNoResumableGame() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.START, screen.controlAt(420, 335));
    }

    @Test
    public void startAloneLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.START, screen.controlAt(START_ALONE_LEFT, ROW1_TOP));
    }

    @Test
    public void startAloneRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(START_ALONE_RIGHT, 335));
        assertNull(screen.controlAt(420, ROW1_BOTTOM));
        assertEquals(StartScreen.Control.START, screen.controlAt(START_ALONE_RIGHT - 1, ROW1_BOTTOM - 1));
    }

    @Test
    public void resumeSlotReturnsNullWhenNoResumableGame() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(300, 335));
        StartScreen screenExplicit = new StartScreen(5, 50, 2, false);
        assertNull(screenExplicit.controlAt(300, 335));
    }

    @Test
    public void startWithResumeSlotReturnsNullWhenNoResumableGame() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(500, 335));
    }

    // --- Row 1: Resume Game + Start Game side by side (resumable game exists) ---

    @Test
    public void clickInsideResumeReturnsResumeWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(300, 335));
    }

    @Test
    public void resumeLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(RESUME_LEFT, ROW1_TOP));
    }

    @Test
    public void resumeRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertNull(screen.controlAt(RESUME_RIGHT, 335));
        assertNull(screen.controlAt(300, ROW1_BOTTOM));
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(RESUME_RIGHT - 1, ROW1_BOTTOM - 1));
    }

    @Test
    public void clickInsideStartWithResumeReturnsStartWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.START, screen.controlAt(500, 335));
    }

    @Test
    public void startWithResumeLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.START, screen.controlAt(START_WITH_RESUME_LEFT, ROW1_TOP));
    }

    @Test
    public void startWithResumeRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertNull(screen.controlAt(START_WITH_RESUME_RIGHT, 335));
        assertNull(screen.controlAt(500, ROW1_BOTTOM));
        assertEquals(StartScreen.Control.START, screen.controlAt(START_WITH_RESUME_RIGHT - 1, ROW1_BOTTOM - 1));
    }

    @Test
    public void startAloneSlotReturnsNullWhenResumableGameExists() {
        // START_ALONE's centered slot (340-500) is not where Start lives once
        // Resume is also shown -- must not still be secretly clickable there.
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertNull(screen.controlAt(420, 335));
    }

    @Test
    public void clickAboveOrBelowRow1ReturnsNull() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertNull(screen.controlAt(300, ROW1_TOP - 1));
        assertNull(screen.controlAt(300, ROW1_BOTTOM));
        assertNull(screen.controlAt(500, ROW1_TOP - 1));
        assertNull(screen.controlAt(500, ROW1_BOTTOM));
    }

    // --- Row 3: Settings, always shown ---

    @Test
    public void clickInsideSettingsReturnsSettings() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(420, 475));
    }

    @Test
    public void clickInsideSettingsReturnsSettingsWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, 50, 2, true);
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(420, 475));
    }

    @Test
    public void settingsLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(SETTINGS_LEFT, ROW3_TOP));
    }

    @Test
    public void settingsRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(SETTINGS_RIGHT, 475));
        assertNull(screen.controlAt(420, ROW3_BOTTOM));
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(SETTINGS_RIGHT - 1, ROW3_BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowSettingsReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(420, ROW3_TOP - 1));
        assertNull(screen.controlAt(420, ROW3_BOTTOM));
    }

    // --- name field / misc ---

    @Test
    public void clickOnNameFieldReturnsNull() {
        StartScreen screen = new StartScreen();
        // field spans x in [270,570), y in [260,294) -- overlaps Row 1/Row 2's
        // x-span but not their y-span, so this must miss everything.
        assertNull(screen.controlAt(300, 275));
        assertNull(screen.controlAt(270, 260));
        assertNull(screen.controlAt(569, 293));
    }

    @Test
    public void clickElsewhereOnCanvasReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(0, 0));
        assertNull(screen.controlAt(420, 550));
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

    // --- Text-fit / non-collision proofs, headless FontMetrics (mirrors this
    // class's own established MAX_NAME_LENGTH/Resume-clearance measurement
    // convention rather than eyeballing) ---

    @Test
    public void everyButtonLabelFitsInsideItsOwnBox() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        FontMetrics metrics = g.getFontMetrics();

        assertFits(metrics, "Resume Game", RESUME_LEFT, RESUME_RIGHT);
        assertFits(metrics, "Start Game", START_WITH_RESUME_LEFT, START_WITH_RESUME_RIGHT);
        assertFits(metrics, "Start Game", START_ALONE_LEFT, START_ALONE_RIGHT);
        assertFits(metrics, "Rules", RULES_LEFT, RULES_RIGHT);
        assertFits(metrics, "Achievements", ACHIEVEMENTS_LEFT, ACHIEVEMENTS_RIGHT);
        assertFits(metrics, "Settings", SETTINGS_LEFT, SETTINGS_RIGHT);

        g.dispose();
    }

    private static void assertFits(FontMetrics metrics, String label, int left, int right) {
        int width = metrics.stringWidth(label);
        assertTrue("\"" + label + "\" (width=" + width + ") must fit inside its box (width="
                        + (right - left) + ")",
                width <= (right - left));
    }

    /**
     * Explicit non-collision proof against the two regions Row 3 (Settings)
     * sits between: the stats line's rendered text (baseline STATS_Y=430)
     * must clear Row 3's top edge, and Row 3's bottom edge must clear the
     * footer's rendered text (baseline FOOTER_Y=560) -- measured via headless
     * FontMetrics against the actual rendered strings, not eyeballed. This
     * reuses the exact slot/clearance the old lone-Resume-button test proved
     * (Settings now occupies that slot) -- see StartScreen's own class doc.
     */
    @Test
    public void row3ClearsStatsLineAndFooterText() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        FontMetrics metrics = g.getFontMetrics();

        int statsBaselineY = 430;
        int statsBottomExtent = statsBaselineY + metrics.getDescent();
        assertTrue("Row 3's top edge (y=" + ROW3_TOP + ") must clear the stats line's bottom extent ("
                        + statsBottomExtent + ")",
                ROW3_TOP >= statsBottomExtent);

        int footerBaselineY = 560;
        int footerTopExtent = footerBaselineY - metrics.getAscent();
        assertTrue("Row 3's bottom edge (y=" + ROW3_BOTTOM + ") must clear the footer text's top extent ("
                        + footerTopExtent + ")",
                ROW3_BOTTOM <= footerTopExtent);

        g.dispose();
    }

    /**
     * Non-collision proof against Row 2 (Rules/Achievements) directly above
     * Row 3 (Settings) -- same rectsOverlap technique TestBetStepper/
     * TestNextTrickPrompt already use for their own hotspots.
     */
    @Test
    public void row3DoesNotOverlapRow2() {
        assertFalse(rectsOverlap(SETTINGS_LEFT, ROW3_TOP, SETTINGS_RIGHT, ROW3_BOTTOM,
                RULES_LEFT, ROW2_TOP, RULES_RIGHT, ROW2_BOTTOM));
        assertFalse(rectsOverlap(SETTINGS_LEFT, ROW3_TOP, SETTINGS_RIGHT, ROW3_BOTTOM,
                ACHIEVEMENTS_LEFT, ROW2_TOP, ACHIEVEMENTS_RIGHT, ROW2_BOTTOM));
    }

    /** Half-open rect intersection test: true iff [aLeft,aRight)x[aTop,aBottom) and [bLeft,bRight)x[bTop,bBottom) share any pixel. */
    private static boolean rectsOverlap(int aLeft, int aTop, int aRight, int aBottom,
                                         int bLeft, int bTop, int bRight, int bBottom) {
        return aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop;
    }
}
