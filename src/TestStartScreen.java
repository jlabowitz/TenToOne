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
 * Rules/Achievements, Stats/Settings layout below).
 *
 * Layout contract (three rows; ROADMAP item 10 follow-up made every
 * two-button row equal-width/20px-gap, sharing the same 270-570 span the
 * name field itself spans -- 140px per button, 20px gap):
 *
 * Row 1 (y=[320,354)): "Resume Game" at x=[270,410) + "New Game" at
 * x=[430,570) when hasResumableGame is true; otherwise "New Game" alone,
 * centered at x=[340,500).
 *
 * Row 2 (y=[370,404)), always both regardless of hasResumableGame: "Rules"
 * at x=[270,410) + "Achievements" at x=[430,570).
 *
 * Row 3 (y=[420,454)): "Stats" at x=[270,410) + "Settings" at x=[430,570)
 * when hasStatsToShow() (gamesPlayed > 0) is true; otherwise "Settings"
 * alone, centered at x=[340,500) (Stats hidden entirely, mirroring Resume's
 * own gating precedent). Code-review fix: tightened from the pre-this-pass
 * [460,494) so the gap after Row 2 (16px) matches the gap between Row 1 and
 * Row 2, closing a 56px leftover gap from the old inline stats text line
 * this row's Stats button replaced.
 */
public class TestStartScreen {
    private static final int ROW1_TOP = 320, ROW1_BOTTOM = 354;
    private static final int ROW2_TOP = 370, ROW2_BOTTOM = 404;
    private static final int ROW3_TOP = 420, ROW3_BOTTOM = 454;

    private static final int RESUME_LEFT = 270, RESUME_RIGHT = 410;
    private static final int START_WITH_RESUME_LEFT = 430, START_WITH_RESUME_RIGHT = 570;
    private static final int START_ALONE_LEFT = 340, START_ALONE_RIGHT = 500;

    private static final int RULES_LEFT = 270, RULES_RIGHT = 410;
    private static final int ACHIEVEMENTS_LEFT = 430, ACHIEVEMENTS_RIGHT = 570;

    private static final int STATS_LEFT = 270, STATS_RIGHT = 410;
    private static final int SETTINGS_WITH_STATS_LEFT = 430, SETTINGS_WITH_STATS_RIGHT = 570;
    private static final int SETTINGS_ALONE_LEFT = 340, SETTINGS_ALONE_RIGHT = 500;

    // --- Row 2: Rules (always present, regardless of hasResumableGame) ---

    @Test
    public void clickInsideRulesReturnsRulesWhenNoResumableGame() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.RULES, screen.controlAt(300, 385));
    }

    @Test
    public void clickInsideRulesReturnsRulesWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, true);
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
        assertEquals(StartScreen.Control.ACHIEVEMENTS, screen.controlAt(500, 385));
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
        assertNull(screen.controlAt(500, ROW2_BOTTOM));
        assertEquals(StartScreen.Control.ACHIEVEMENTS,
                screen.controlAt(ACHIEVEMENTS_RIGHT - 1, ROW2_BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowRow2ReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(300, ROW2_TOP - 1));
        assertNull(screen.controlAt(300, ROW2_BOTTOM));
        assertNull(screen.controlAt(500, ROW2_TOP - 1));
        assertNull(screen.controlAt(500, ROW2_BOTTOM));
    }

    @Test
    public void clickInGapBetweenRulesAndAchievementsReturnsNull() {
        StartScreen screen = new StartScreen();
        // Dead zone between Rules [270,410) and Achievements [430,570)
        assertNull(screen.controlAt(420, 385));
    }

    // --- Row 1: New Game alone (no resumable game) ---

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
        StartScreen screenExplicit = new StartScreen(5, false);
        assertNull(screenExplicit.controlAt(300, 335));
    }

    @Test
    public void startWithResumeSlotReturnsNullWhenNoResumableGame() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(500, 335));
    }

    // --- Row 1: Resume Game + New Game side by side (resumable game exists) ---

    @Test
    public void clickInsideResumeReturnsResumeWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, true);
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(300, 335));
    }

    @Test
    public void resumeLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen(5, true);
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(RESUME_LEFT, ROW1_TOP));
    }

    @Test
    public void resumeRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen(5, true);
        assertNull(screen.controlAt(RESUME_RIGHT, 335));
        assertNull(screen.controlAt(300, ROW1_BOTTOM));
        assertEquals(StartScreen.Control.RESUME, screen.controlAt(RESUME_RIGHT - 1, ROW1_BOTTOM - 1));
    }

    @Test
    public void clickInsideStartWithResumeReturnsStartWhenResumableGameExists() {
        StartScreen screen = new StartScreen(5, true);
        assertEquals(StartScreen.Control.START, screen.controlAt(500, 335));
    }

    @Test
    public void startWithResumeLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen(5, true);
        assertEquals(StartScreen.Control.START, screen.controlAt(START_WITH_RESUME_LEFT, ROW1_TOP));
    }

    @Test
    public void startWithResumeRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen(5, true);
        assertNull(screen.controlAt(START_WITH_RESUME_RIGHT, 335));
        assertNull(screen.controlAt(500, ROW1_BOTTOM));
        assertEquals(StartScreen.Control.START, screen.controlAt(START_WITH_RESUME_RIGHT - 1, ROW1_BOTTOM - 1));
    }

    @Test
    public void startAloneSlotReturnsNullWhenResumableGameExists() {
        // START_ALONE's centered slot (340-500) is not where Start lives once
        // Resume is also shown -- must not still be secretly clickable there.
        StartScreen screen = new StartScreen(5, true);
        assertNull(screen.controlAt(420, 335));
    }

    @Test
    public void clickAboveOrBelowRow1ReturnsNull() {
        StartScreen screen = new StartScreen(5, true);
        assertNull(screen.controlAt(300, ROW1_TOP - 1));
        assertNull(screen.controlAt(300, ROW1_BOTTOM));
        assertNull(screen.controlAt(500, ROW1_TOP - 1));
        assertNull(screen.controlAt(500, ROW1_BOTTOM));
    }

    // --- Row 3: Settings alone, no stats yet (gamesPlayed == 0) ---

    @Test
    public void clickInsideSettingsAloneReturnsSettingsWhenNoStats() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(420, 435));
    }

    @Test
    public void settingsAloneLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen();
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(SETTINGS_ALONE_LEFT, ROW3_TOP));
    }

    @Test
    public void settingsAloneRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(SETTINGS_ALONE_RIGHT, 435));
        assertNull(screen.controlAt(420, ROW3_BOTTOM));
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(SETTINGS_ALONE_RIGHT - 1, ROW3_BOTTOM - 1));
    }

    @Test
    public void statsSlotReturnsNullWhenNoStats() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(300, 435));
    }

    @Test
    public void clickAboveOrBelowSettingsAloneReturnsNull() {
        StartScreen screen = new StartScreen();
        assertNull(screen.controlAt(420, ROW3_TOP - 1));
        assertNull(screen.controlAt(420, ROW3_BOTTOM));
    }

    // --- Row 3: Stats + Settings side by side (gamesPlayed > 0) ---

    @Test
    public void clickInsideStatsReturnsStatsWhenStatsExist() {
        StartScreen screen = new StartScreen(5);
        assertEquals(StartScreen.Control.STATS, screen.controlAt(300, 435));
    }

    @Test
    public void statsLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen(5);
        assertEquals(StartScreen.Control.STATS, screen.controlAt(STATS_LEFT, ROW3_TOP));
    }

    @Test
    public void statsRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen(5);
        assertNull(screen.controlAt(STATS_RIGHT, 435));
        assertNull(screen.controlAt(300, ROW3_BOTTOM));
        assertEquals(StartScreen.Control.STATS, screen.controlAt(STATS_RIGHT - 1, ROW3_BOTTOM - 1));
    }

    @Test
    public void clickInsideSettingsWithStatsReturnsSettingsWhenStatsExist() {
        StartScreen screen = new StartScreen(5, true);
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(500, 435));
    }

    @Test
    public void settingsWithStatsLeftAndTopBoundaryIsInclusive() {
        StartScreen screen = new StartScreen(5);
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(SETTINGS_WITH_STATS_LEFT, ROW3_TOP));
    }

    @Test
    public void settingsWithStatsRightAndBottomBoundaryIsExclusive() {
        StartScreen screen = new StartScreen(5);
        assertNull(screen.controlAt(SETTINGS_WITH_STATS_RIGHT, 435));
        assertNull(screen.controlAt(500, ROW3_BOTTOM));
        assertEquals(StartScreen.Control.SETTINGS, screen.controlAt(SETTINGS_WITH_STATS_RIGHT - 1, ROW3_BOTTOM - 1));
    }

    @Test
    public void settingsAloneSlotReturnsNullWhenStatsExist() {
        // SETTINGS_ALONE's centered slot (340-500) is not where Settings lives
        // once Stats is also shown -- must not still be secretly clickable there.
        StartScreen screen = new StartScreen(5);
        assertNull(screen.controlAt(420, 435));
    }

    @Test
    public void clickInGapBetweenStatsAndSettingsReturnsNull() {
        StartScreen screen = new StartScreen(5);
        // Dead zone between Stats [270,410) and Settings [430,570)
        assertNull(screen.controlAt(420, 435));
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

    // --- ROADMAP item 2/10: Stats button, only shown once gamesPlayed > 0 ---

    @Test
    public void noArgConstructorDefaultsToNoStatsShown() {
        StartScreen screen = new StartScreen();
        assertFalse(screen.hasStatsToShow());
    }

    @Test
    public void statsConstructorWithZeroGamesPlayedHidesStatsButton() {
        StartScreen screen = new StartScreen(0);
        assertFalse(screen.hasStatsToShow());
    }

    @Test
    public void statsConstructorWithGamesPlayedShowsStatsButton() {
        StartScreen screen = new StartScreen(12);
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

    // --- ROADMAP item 10 follow-up: lastUsedName pre-population ---

    @Test
    public void fiveArgConstructorPrepopulatesNameFromLastUsedName() {
        StartScreen screen = new StartScreen(5, false, "Alex");
        assertEquals("Alex", screen.getName());
    }

    @Test
    public void nullLastUsedNameIsTreatedAsEmpty() {
        StartScreen screen = new StartScreen(0, false, null);
        assertEquals("", screen.getName());
    }

    @Test
    public void overlongLastUsedNameIsClampedToMaxNameLength() {
        String tooLong = "W".repeat(StartScreen.MAX_NAME_LENGTH + 5);
        StartScreen screen = new StartScreen(0, false, tooLong);
        assertEquals(StartScreen.MAX_NAME_LENGTH, screen.getName().length());
    }

    @Test
    public void fourArgConstructorStillDefaultsNameToEmpty() {
        // Backward-compat overload used by every pre-this-pass call site/test.
        StartScreen screen = new StartScreen(5, true);
        assertEquals("", screen.getName());
    }

    // --- ROADMAP item 10 follow-up: select-all + type-replaces ---

    @Test
    public void selectAllThenTypeCharReplacesTheWholeBuffer() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('l');
        screen.typeChar('e');
        screen.typeChar('x');
        screen.selectAll();

        screen.typeChar('Z');

        assertEquals("Z", screen.getName());
    }

    /**
     * ROADMAP item 10 follow-up (second pass): user feedback was that
     * select-all should visibly highlight the text "so I can delete it" --
     * the first pass's backspace() only cancelled the selection flag and
     * then ran one ordinary char-delete, which didn't match that
     * expectation (or any real text field's own select-all+Backspace
     * convention: delete the whole highlighted selection). Renamed from
     * selectAllIsClearedByBackspaceWithoutClearingTheBuffer to reflect the
     * corrected behavior.
     */
    @Test
    public void selectAllThenBackspaceDeletesTheWholeSelection() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('l');
        screen.selectAll();

        screen.backspace();

        assertEquals("", screen.getName());
        // selection must be cleared afterward -- a further typed char
        // appends normally, not replaces (there's nothing left to replace).
        screen.typeChar('z');
        assertEquals("z", screen.getName());
    }

    @Test
    public void selectAllIsClearedByDeleteWord() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('l');
        screen.selectAll();

        screen.deleteWord();

        assertEquals("", screen.getName());
        screen.typeChar('Q');
        assertEquals("Q", screen.getName());
    }

    @Test
    public void selectAllOnEmptyBufferThenTypingJustTypesNormally() {
        StartScreen screen = new StartScreen();
        screen.selectAll();
        screen.typeChar('A');
        assertEquals("A", screen.getName());
    }

    // --- ROADMAP item 10 follow-up: deleteWord (Ctrl+Backspace) ---

    @Test
    public void deleteWordRemovesTrailingWordWithNoSpaces() {
        assertEquals("", StartScreen.deleteTrailingWord("Alex"));
    }

    @Test
    public void deleteWordRemovesOnlyTheLastWordKeepingPrecedingSpace() {
        assertEquals("Alex ", StartScreen.deleteTrailingWord("Alex Smith"));
    }

    @Test
    public void deleteWordRemovesTrailingWhitespaceThenTheWordBeforeIt() {
        assertEquals("hello ", StartScreen.deleteTrailingWord("hello world "));
    }

    @Test
    public void deleteWordOnEmptyStringIsANoOp() {
        assertEquals("", StartScreen.deleteTrailingWord(""));
    }

    @Test
    public void deleteWordOnAllWhitespaceClearsTheBuffer() {
        assertEquals("", StartScreen.deleteTrailingWord("   "));
    }

    @Test
    public void deleteWordOnInstanceMutatesTheNameBuffer() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('l');
        screen.typeChar('e');
        screen.typeChar('x');
        screen.typeChar(' ');
        screen.typeChar('S');

        screen.deleteWord();

        assertEquals("Alex ", screen.getName());
    }

    // --- ROADMAP item 10 follow-up (second pass): cursor-relative editing,
    // moveCursorLeft/Right, and click-to-position (user feedback: select-all
    // had no visible indicator, and arrow keys/click-to-position didn't work
    // at all -- the first pass's append-only model is replaced by a real
    // cursor index) ---

    @Test
    public void moveCursorLeftThenTypeCharInsertsInTheMiddle() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('x');
        screen.moveCursorLeft();
        screen.typeChar('B');
        assertEquals("ABx", screen.getName());
    }

    @Test
    public void moveCursorLeftClampsAtStart() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.moveCursorLeft();
        screen.moveCursorLeft();
        screen.typeChar('B');
        assertEquals("BA", screen.getName());
    }

    @Test
    public void moveCursorRightClampsAtEnd() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.moveCursorLeft();
        screen.moveCursorRight();
        screen.moveCursorRight();
        screen.typeChar('B');
        assertEquals("AB", screen.getName());
    }

    @Test
    public void backspaceDeletesTheCharacterBeforeTheCursorNotAlwaysTheLastOne() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.typeChar('C');
        screen.moveCursorLeft();
        screen.backspace();
        assertEquals("AC", screen.getName());
    }

    @Test
    public void deleteWordOperatesRelativeToTheCursorLeavingTextAfterItIntact() {
        StartScreen screen = new StartScreen();
        for (char c : "Alex Smith".toCharArray()) {
            screen.typeChar(c);
        }
        // cursor at the end; move left past "Smith" (5 chars) to sit right
        // after the space, then delete the word before it
        for (int i = 0; i < 5; i++) {
            screen.moveCursorLeft();
        }
        screen.deleteWord();
        assertEquals("Smith", screen.getName());
    }

    @Test
    public void moveCursorLeftOnEmptyNameIsNoOp() {
        StartScreen screen = new StartScreen();
        screen.moveCursorLeft();
        screen.typeChar('A');
        assertEquals("A", screen.getName());
    }

    @Test
    public void selectAllThenMoveCursorLeftCollapsesToStartWithoutDeleting() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.selectAll();
        screen.moveCursorLeft();
        // selection must be gone (collapsed to start) but nothing deleted
        assertEquals("AB", screen.getName());
        screen.typeChar('Z');
        assertEquals("ZAB", screen.getName());
    }

    @Test
    public void selectAllThenMoveCursorRightCollapsesToEndWithoutDeleting() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.selectAll();
        screen.moveCursorRight();
        assertEquals("AB", screen.getName());
        screen.typeChar('Z');
        assertEquals("ABZ", screen.getName());
    }

    // --- ROADMAP item 10 follow-up (third pass): Ctrl+Left/Right word-jump ---

    @Test
    public void previousWordBoundarySkipsTrailingSpaceThenTheWordBeforeIt() {
        assertEquals(5, StartScreen.previousWordBoundary("Alex Smith", 10));
        assertEquals(0, StartScreen.previousWordBoundary("Alex Smith", 5));
        assertEquals(0, StartScreen.previousWordBoundary("Alex", 4));
    }

    @Test
    public void previousWordBoundaryOnMidWordPositionGoesToStartOfThatWord() {
        assertEquals(5, StartScreen.previousWordBoundary("Alex Smith", 8));
    }

    @Test
    public void previousWordBoundaryAtStartIsANoOp() {
        assertEquals(0, StartScreen.previousWordBoundary("Alex", 0));
    }

    @Test
    public void nextWordBoundarySkipsLeadingSpaceThenTheWordAfterIt() {
        assertEquals(4, StartScreen.nextWordBoundary("Alex Smith", 0));
        assertEquals(10, StartScreen.nextWordBoundary("Alex Smith", 4));
        assertEquals(10, StartScreen.nextWordBoundary("Alex Smith", 5));
    }

    @Test
    public void nextWordBoundaryOnMidWordPositionGoesToEndOfThatWord() {
        assertEquals(4, StartScreen.nextWordBoundary("Alex Smith", 2));
    }

    @Test
    public void nextWordBoundaryAtEndIsANoOp() {
        assertEquals(4, StartScreen.nextWordBoundary("Alex", 4));
    }

    @Test
    public void moveWordLeftOnInstanceJumpsCursorToStartOfPreviousWord() {
        StartScreen screen = new StartScreen();
        for (char c : "Alex Smith".toCharArray()) {
            screen.typeChar(c);
        }
        screen.moveWordLeft();
        screen.typeChar('_');
        assertEquals("Alex _Smith", screen.getName());
    }

    @Test
    public void moveWordLeftTwiceReachesTheStart() {
        StartScreen screen = new StartScreen();
        for (char c : "Alex Smith".toCharArray()) {
            screen.typeChar(c);
        }
        screen.moveWordLeft();
        screen.moveWordLeft();
        screen.typeChar('_');
        assertEquals("_Alex Smith", screen.getName());
    }

    @Test
    public void moveWordRightOnInstanceJumpsCursorToEndOfNextWord() {
        StartScreen screen = new StartScreen();
        for (char c : "Alex Smith".toCharArray()) {
            screen.typeChar(c);
        }
        screen.moveWordLeft();
        screen.moveWordLeft();
        screen.moveWordRight();
        screen.typeChar('_');
        assertEquals("Alex_ Smith", screen.getName());
    }

    @Test
    public void moveWordLeftOnEmptyNameIsNoOp() {
        StartScreen screen = new StartScreen();
        screen.moveWordLeft();
        screen.typeChar('A');
        assertEquals("A", screen.getName());
    }

    @Test
    public void selectAllThenMoveWordLeftCollapsesToStartWithoutDeleting() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.selectAll();
        screen.moveWordLeft();
        assertEquals("AB", screen.getName());
        screen.typeChar('Z');
        assertEquals("ZAB", screen.getName());
    }

    @Test
    public void selectAllThenMoveWordRightCollapsesToEndWithoutDeleting() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.selectAll();
        screen.moveWordRight();
        assertEquals("AB", screen.getName());
        screen.typeChar('Z');
        assertEquals("ABZ", screen.getName());
    }

    // --- ROADMAP item 10 follow-up (fourth pass): standard OS convention --
    // the blinking caret goes solid (and the blink cycle resets) on any
    // cursor-moving action instead of continuing to blink mid-navigation ---

    /** Advances SCREEN's tick() until the blink toggles off (bounded loop -- blinks roughly every 30 ticks per the class's own doc), so a subsequent action's reset is actually observable. */
    private static void advanceUntilBlinkOff(StartScreen screen) {
        for (int i = 0; i < 40 && screen.isCursorBlinkVisible(); i++) {
            screen.tick();
        }
        assertFalse("test setup: blink must have toggled off after enough idle ticks", screen.isCursorBlinkVisible());
    }

    @Test
    public void typeCharResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        advanceUntilBlinkOff(screen);
        screen.typeChar('A');
        assertTrue(screen.isCursorBlinkVisible());
    }

    @Test
    public void backspaceResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        advanceUntilBlinkOff(screen);
        screen.backspace();
        assertTrue(screen.isCursorBlinkVisible());
    }

    @Test
    public void deleteWordResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        advanceUntilBlinkOff(screen);
        screen.deleteWord();
        assertTrue(screen.isCursorBlinkVisible());
    }

    @Test
    public void moveCursorLeftResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        advanceUntilBlinkOff(screen);
        screen.moveCursorLeft();
        assertTrue(screen.isCursorBlinkVisible());
    }

    @Test
    public void moveCursorRightResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.moveCursorLeft();
        advanceUntilBlinkOff(screen);
        screen.moveCursorRight();
        assertTrue(screen.isCursorBlinkVisible());
    }

    @Test
    public void moveWordLeftResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        advanceUntilBlinkOff(screen);
        screen.moveWordLeft();
        assertTrue(screen.isCursorBlinkVisible());
    }

    @Test
    public void moveWordRightResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.moveWordLeft();
        advanceUntilBlinkOff(screen);
        screen.moveWordRight();
        assertTrue(screen.isCursorBlinkVisible());
    }

    @Test
    public void clickNameFieldResetsTheBlinkToSolidVisible() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        advanceUntilBlinkOff(screen);
        screen.clickNameField(271);
        assertTrue(screen.isCursorBlinkVisible());
    }

    // --- ROADMAP item 10 follow-up (second pass): click-to-position ---

    @Test
    public void isNameFieldMatchesTheFieldBoundsExactly() {
        StartScreen screen = new StartScreen();
        assertTrue(screen.isNameField(300, 275));
        assertTrue(screen.isNameField(270, 260)); // FIELD_LEFT/TOP inclusive
        assertTrue(screen.isNameField(569, 293)); // FIELD_RIGHT/BOTTOM - 1, still inside
        assertFalse(screen.isNameField(570, 275)); // FIELD_RIGHT itself, exclusive
        assertFalse(screen.isNameField(300, 294)); // FIELD_BOTTOM itself, exclusive
        assertFalse(screen.isNameField(269, 275)); // just left of FIELD_LEFT
    }

    @Test
    public void clickNameFieldAtTheFieldsLeftEdgePlacesCursorAtStart() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.typeChar('C');
        screen.clickNameField(271); // just inside FIELD_LEFT, well left of any text
        screen.typeChar('Z');
        assertEquals("ZABC", screen.getName());
    }

    @Test
    public void clickNameFieldFarPastTheTextPlacesCursorAtEnd() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.clickNameField(560); // near FIELD_RIGHT, well past 2 chars of text
        screen.typeChar('Z');
        assertEquals("ABZ", screen.getName());
    }

    @Test
    public void clickNameFieldClearsAnActiveSelectionInsteadOfDeleting() {
        StartScreen screen = new StartScreen();
        screen.typeChar('A');
        screen.typeChar('B');
        screen.selectAll();
        screen.clickNameField(271);
        assertEquals("AB", screen.getName());
        screen.typeChar('Z');
        assertEquals("ZAB", screen.getName());
    }

    // --- ROADMAP item 10 follow-up: submit() / consumeSubmitRequested() ---

    @Test
    public void submitSetsThePendingFlagConsumedExactlyOnce() {
        StartScreen screen = new StartScreen();
        assertFalse(screen.consumeSubmitRequested());

        screen.submit();

        assertTrue(screen.consumeSubmitRequested());
        assertFalse("a second consume without another submit() must report nothing pending",
                screen.consumeSubmitRequested());
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
        assertFits(metrics, "New Game", START_WITH_RESUME_LEFT, START_WITH_RESUME_RIGHT);
        assertFits(metrics, "New Game", START_ALONE_LEFT, START_ALONE_RIGHT);
        assertFits(metrics, "Rules", RULES_LEFT, RULES_RIGHT);
        assertFits(metrics, "Achievements", ACHIEVEMENTS_LEFT, ACHIEVEMENTS_RIGHT);
        assertFits(metrics, "Stats", STATS_LEFT, STATS_RIGHT);
        assertFits(metrics, "Settings", SETTINGS_WITH_STATS_LEFT, SETTINGS_WITH_STATS_RIGHT);
        assertFits(metrics, "Settings", SETTINGS_ALONE_LEFT, SETTINGS_ALONE_RIGHT);

        g.dispose();
    }

    /**
     * Locks in the actual measured width of "Achievements" (the longest of
     * the six button labels, and the binding constraint on the 140px paired
     * button width -- see StartScreen's own class doc) against Dialog 12pt
     * plain (the font actually in effect at that draw call -- no explicit
     * setFont precedes it in render()), so a future font change can't
     * silently make this button overflow without a test noticing.
     */
    @Test
    public void achievementsLabelMeasuredWidthFitsComfortablyInsidePairedButtonWidth() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        FontMetrics metrics = g.getFontMetrics();

        int width = metrics.stringWidth("Achievements");
        int pairedButtonWidth = ACHIEVEMENTS_RIGHT - ACHIEVEMENTS_LEFT;
        assertEquals(77, width);
        assertTrue("\"Achievements\" (width=" + width + ") must fit legibly (with real padding) inside a "
                        + pairedButtonWidth + "px button", width + 20 <= pairedButtonWidth);

        g.dispose();
    }

    private static void assertFits(FontMetrics metrics, String label, int left, int right) {
        int width = metrics.stringWidth(label);
        assertTrue("\"" + label + "\" (width=" + width + ") must fit inside its box (width="
                        + (right - left) + ")",
                width <= (right - left));
    }
}
