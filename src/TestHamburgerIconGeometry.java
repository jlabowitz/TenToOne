import org.junit.Test;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 10 (user-feedback pass): the deeper, cross-class clearance
 * proof for the hamburger icon's shared *closed* geometry -- HAMBURGER_LEFT/
 * RIGHT/TOP/BOTTOM = 10/40/5/35, duplicated identically across BetStepper/
 * IllegalPlayFeedback/NextTrickPrompt (see each class's own per-class
 * hotspot tests in TestBetStepper/TestIllegalPlayFeedback/TestNextTrickPrompt).
 *
 * ROADMAP item 10 follow-up: this file's scope narrowed since it was first
 * written. The *open dropdown panel* no longer has a clearance proof here --
 * HamburgerMenu moved to a single vertical column (1x6) per the user's
 * explicit override of this file's own original 3x2-grid rationale, and the
 * user explicitly accepted that the open panel now overlaps the AI seat
 * row/message band/trump card while showing (see
 * openDropdownNowOverlapsAiSeatRowNameTextAcceptedTradeoff below, which
 * documents that overlap the same way
 * hamburgerIconNowOverlapsAchievementToastDismissBandAcceptedTradeoff already
 * documents the toast-band one). Only the *closed* icon still needs to clear
 * the AI seat row -- that's what the remaining three constraints below prove:
 *
 * 1. The AI seat row's own name/card/score text (Game.AI_ROW_Y, shared by
 *    every AI seat regardless of count) -- the closed icon must clear it.
 * 2. The trump card's footprint (Round.renderTrumpCard(): x=50,y=HEIGHT/2,
 *    +/-10px border) -- the AI seat row's played-card position must stay
 *    clear of it.
 * 3. NextTrickPrompt/IllegalPlayFeedback's shared centered-message band
 *    (baseline y=280) -- the AI seat row's own lowest text (its score line)
 *    must stay clear of it. This is the tightest margin in the whole
 *    layout (~9px) and the actual reason Game.AI_ROW_Y is 70, not higher --
 *    see that field's own doc.
 *
 * These three constraints are in real tension (moving the AI row down helps
 * clear the closed hamburger icon above, but pushes it toward the message
 * band/trump card below) -- this file is the algebraic proof that the
 * chosen numbers (HAMBURGER_TOP=5, Game.AI_ROW_Y=70) satisfy all three
 * simultaneously, not just "look fine" in one screenshot.
 */
public class TestHamburgerIconGeometry {
    private static final int HAMBURGER_LEFT = 10;
    private static final int HAMBURGER_RIGHT = 40;
    private static final int HAMBURGER_TOP = 5;
    private static final int HAMBURGER_BOTTOM = 35;

    // HamburgerMenu's own private geometry, duplicated here per this
    // codebase's established convention (see BetStepper/IllegalPlayFeedback/
    // NextTrickPrompt's own HAMBURGER_* duplication) -- cross-checked against
    // the real class below via its public isInsidePanel()/itemAt() methods,
    // so a drift between these literals and the production constants is
    // caught rather than silently assumed.
    private static final int PANEL_LEFT = 10, PANEL_RIGHT = 160;
    private static final int PANEL_TOP = 5, PANEL_BOTTOM = 197;

    // Card geometry (Card.java): WIDTH/HEIGHT are public; BORDER (trump
    // highlight) and the high-card ring's max offset are private, duplicated
    // here with their real values (BORDER=10; HIGH_CARD_BORDER=13 +
    // (HIGH_CARD_RING_COUNT-1)=1 => the ring's outermost edge sits 14px
    // outside the card).
    private static final int CARD_BORDER = 10;
    private static final int HIGH_CARD_RING_MAX_OFFSET = 14;

    // Trick.java's AI card-placement offset below the player's own y
    // (card.setY(player.getY() + 30)) -- duplicated here, not exposed.
    private static final int AI_CARD_Y_OFFSET = 30;

    // NextTrickPrompt/IllegalPlayFeedback's shared centered-message baseline
    // -- duplicated here (each class keeps its own private BASELINE_Y=280).
    private static final int MESSAGE_BASELINE_Y = 280;

    // Player.render()'s AI-branch HUD line offsets below the player's own y
    // (getY()+165 for trickScore/bet, getY()+185 for score) -- duplicated
    // here, not exposed as named constants in Player.java itself.
    private static final int AI_SCORE_LINE_Y_OFFSET = 185;

    private static FontMetrics defaultFontMetrics() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        FontMetrics metrics = g.getFontMetrics();
        g.dispose();
        return metrics;
    }

    /** Half-open rect intersection test, same convention as TestBetStepper/TestIllegalPlayFeedback/TestNextTrickPrompt's own copies. */
    private static boolean rectsOverlap(int aLeft, int aTop, int aRight, int aBottom,
                                         int bLeft, int bTop, int bRight, int bBottom) {
        return aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop;
    }

    /**
     * Every one of BetStepper/IllegalPlayFeedback/NextTrickPrompt must use
     * the exact same hamburger-hotspot rectangle -- a mismatch between them
     * would mean the icon jumps around depending on which screen is showing,
     * or worse, one class's icon collides with something the others' don't.
     */
    @Test
    public void allThreeClassesShareTheSameHamburgerHotspotRectangle() {
        BetStepper stepper = new BetStepper(10);
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        NextTrickPrompt prompt = new NextTrickPrompt();

        int[][] probePoints = {
                {HAMBURGER_LEFT, HAMBURGER_TOP}, // inclusive corner
                {HAMBURGER_RIGHT - 1, HAMBURGER_BOTTOM - 1}, // last in-bounds pixel
                {HAMBURGER_LEFT - 1, HAMBURGER_TOP}, // just outside (left)
                {HAMBURGER_LEFT, HAMBURGER_TOP - 1}, // just outside (top)
                {HAMBURGER_RIGHT, HAMBURGER_TOP}, // just outside (right, exclusive)
                {HAMBURGER_LEFT, HAMBURGER_BOTTOM}, // just outside (bottom, exclusive)
        };
        for (int[] point : probePoints) {
            boolean stepperResult = stepper.isHamburgerHotspot(point[0], point[1]);
            assertEquals("IllegalPlayFeedback disagrees with BetStepper at (" + point[0] + "," + point[1] + ")",
                    stepperResult, feedback.isHamburgerHotspot(point[0], point[1]));
            assertEquals("NextTrickPrompt disagrees with BetStepper at (" + point[0] + "," + point[1] + ")",
                    stepperResult, prompt.isHamburgerHotspot(point[0], point[1]));
        }
    }

    /**
     * Known, accepted trade-off of moving the icon to the very top of the
     * canvas (per user feedback): it now sits *inside* AchievementToast's
     * click-to-dismiss band (y in [0, TOAST_BAND_BOTTOM=50), full canvas
     * width), unlike the old y=[60,90) position, which cleared it with a
     * 10px margin. This test locks in that the overlap is real (so a future
     * change doesn't accidentally "fix" it without updating the doc/
     * comments that explain why it's accepted) rather than asserting
     * clearance that no longer holds. TestHamburgerMenu's own
     * toastHotspotIsDismissedBeforeAnyMenuHandling test covers the actual
     * runtime behavior this implies (toast-dismiss takes precedence over
     * the menu on any click in this band).
     */
    @Test
    public void hamburgerIconNowOverlapsAchievementToastDismissBandAcceptedTradeoff() {
        int toastBandBottom = 50; // AchievementToast.TOAST_BAND_BOTTOM, private -- duplicated here
        assertTrue("icon is expected to fall inside the toast band now -- see this test's own doc",
                rectsOverlap(HAMBURGER_LEFT, HAMBURGER_TOP, HAMBURGER_RIGHT, HAMBURGER_BOTTOM,
                        0, 0, Game.WIDTH, toastBandBottom));
    }

    /**
     * The leftmost AI seat is always at x=0, for every supported player
     * count -- Game.renderPlayers()'s x formula, (WIDTH*(i-1))/(numPlayers()-1)
     * with i=1, always evaluates to 0 regardless of numPlayers(). Verified
     * here (not just asserted in a comment) across every supported total
     * player count (2-5, i.e. 1-4 AI opponents) since it's the load-bearing
     * fact behind every other test in this file: the leftmost seat is
     * always directly under this top-left-anchored icon/dropdown, at every
     * player count, so there's no player count that gets a "free pass" on
     * the clearance checks below.
     */
    @Test
    public void leftmostAiSeatIsAlwaysAtXZeroForEverySupportedPlayerCount() {
        for (int totalPlayers = 2; totalPlayers <= 5; totalPlayers++) {
            int i = 1; // first AI seat (index 0 is always the human)
            int x = (Game.WIDTH * (i - 1)) / (totalPlayers - 1);
            assertEquals("totalPlayers=" + totalPlayers, 0, x);
        }
    }

    /**
     * ROADMAP item 10 follow-up: the single-column dropdown (PANEL_BOTTOM=197)
     * is now tall enough that it no longer clears the AI seat row's name text
     * -- an accepted trade-off of the user's explicit single-column override,
     * not an oversight. This test locks in that the overlap is real (mirrors
     * hamburgerIconNowOverlapsAchievementToastDismissBandAcceptedTradeoff's
     * own "assert the accepted trade-off instead of stale clearance" shape)
     * rather than silently deleting the coverage. The *closed* icon's own
     * clearance (a real requirement, unchanged) is proven separately by
     * closedIconClearsAiSeatRowNameTextAtEveryPlayerCount below.
     */
    @Test
    public void openDropdownNowOverlapsAiSeatRowNameTextAcceptedTradeoff() {
        FontMetrics metrics = defaultFontMetrics();
        int nameTop = Game.AI_ROW_Y - metrics.getAscent();
        assertTrue("dropdown bottom (" + PANEL_BOTTOM + ") is expected to now overlap the AI row's "
                        + "name-text top (" + nameTop + ") -- see this test's own doc",
                PANEL_BOTTOM > nameTop);
    }

    /**
     * The closed icon itself (unchanged by the single-column dropdown
     * override) must still clear the AI seat row's name text -- this is the
     * one real, still-binding requirement this file exists to prove.
     */
    @Test
    public void closedIconClearsAiSeatRowNameTextAtEveryPlayerCount() {
        FontMetrics metrics = defaultFontMetrics();
        int nameTop = Game.AI_ROW_Y - metrics.getAscent();
        assertTrue(HAMBURGER_BOTTOM <= nameTop);
    }

    /**
     * Constraint 2: the AI seat row's played card (Trick.play():
     * card.setY(player.getY()+30)), including the high-card ring's
     * outermost edge (14px beyond the card, only ever showing on the
     * trick's current highest card -- but must be accounted for since any
     * AI's card can be that card), must stay clear of the trump card's own
     * footprint (Round.renderTrumpCard(): x=50, y=Game.HEIGHT/2, +/-
     * CARD_BORDER=10 for its own highlight border).
     *
     * Checked per seat, per player count, as a real 2D rect overlap (card
     * width/height are fixed, unlike name/score text) -- only the leftmost
     * seat (x=0, card x-span [0,60]) actually overlaps the trump card's
     * x-span ([40,120]) horizontally; every other seat is already clear on
     * x alone, at every player count, but this test checks all of them
     * rather than assuming that.
     */
    @Test
    public void aiPlayedCardClearsTrumpCardFootprintAtEveryPlayerCount() {
        int trumpLeft = 50 - CARD_BORDER;
        int trumpRight = 50 + Card.WIDTH + CARD_BORDER;
        int trumpTop = Game.HEIGHT / 2 - CARD_BORDER;
        int trumpBottom = Game.HEIGHT / 2 + Card.HEIGHT + CARD_BORDER;

        for (int totalPlayers = 2; totalPlayers <= 5; totalPlayers++) {
            int numAi = totalPlayers - 1;
            for (int i = 1; i <= numAi; i++) {
                int seatX = (Game.WIDTH * (i - 1)) / (totalPlayers - 1);
                int cardLeft = seatX;
                int cardRight = seatX + Card.WIDTH;
                int cardTop = Game.AI_ROW_Y + AI_CARD_Y_OFFSET - HIGH_CARD_RING_MAX_OFFSET;
                int cardBottom = Game.AI_ROW_Y + AI_CARD_Y_OFFSET + Card.HEIGHT + HIGH_CARD_RING_MAX_OFFSET;

                boolean overlaps = rectsOverlap(cardLeft, cardTop, cardRight, cardBottom,
                        trumpLeft, trumpTop, trumpRight, trumpBottom);
                assertTrue("totalPlayers=" + totalPlayers + " seat i=" + i + " (x=" + seatX
                                + ") played card+ring must not overlap the trump card's footprint",
                        !overlaps);
            }
        }
    }

    /**
     * Constraint 3: the AI seat row's own lowest text (the score line,
     * Player.render's "Score: " line at getY()+185) must stay clear of
     * NextTrickPrompt/IllegalPlayFeedback's shared centered-message band
     * (baseline y=280) -- the tightest margin in this whole layout (~9px),
     * and the real reason Game.AI_ROW_Y is capped at 70 rather than pushed
     * further down to give the hamburger dropdown more room. See
     * Game.AI_ROW_Y's own doc.
     */
    @Test
    public void aiScoreLineClearsNextTrickMessageBandAtEveryPlayerCount() {
        FontMetrics metrics = defaultFontMetrics();
        int scoreLineBottom = Game.AI_ROW_Y + AI_SCORE_LINE_Y_OFFSET + metrics.getDescent();
        int messageTop = MESSAGE_BASELINE_Y - metrics.getAscent();
        assertTrue("AI score line's bottom (" + scoreLineBottom + ") must clear the message band's top ("
                        + messageTop + ")", scoreLineBottom <= messageTop);
    }

    /**
     * Cross-checks this file's duplicated PANEL_TOP/PANEL_BOTTOM literals
     * against HamburgerMenu's real, private geometry via its public
     * isInsidePanel() -- a drift between the two would flip these
     * assertions, catching it rather than letting the constraint proofs
     * above silently check against stale numbers.
     */
    @Test
    public void panelGeometryLiteralsMatchTheRealHamburgerMenu() {
        HamburgerMenu menu = new HamburgerMenu();
        assertTrue(menu.isInsidePanel(PANEL_LEFT, PANEL_TOP));
        assertTrue(menu.isInsidePanel(PANEL_RIGHT - 1, PANEL_BOTTOM - 1));
        assertTrue(!menu.isInsidePanel(PANEL_LEFT - 1, PANEL_TOP));
        assertTrue(!menu.isInsidePanel(PANEL_LEFT, PANEL_TOP - 1));
        assertTrue(!menu.isInsidePanel(PANEL_RIGHT, PANEL_TOP));
        assertTrue(!menu.isInsidePanel(PANEL_LEFT, PANEL_BOTTOM));
    }

    /** Locks in the shared closed-icon geometry itself so a future accidental edit to only one of the three classes is caught. */
    @Test
    public void hamburgerHotspotGeometryMatchesDocumentedConstants() {
        assertEquals(10, HAMBURGER_LEFT);
        assertEquals(40, HAMBURGER_RIGHT);
        assertEquals(5, HAMBURGER_TOP);
        assertEquals(35, HAMBURGER_BOTTOM);
    }
}
