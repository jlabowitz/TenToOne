import java.awt.*;

/**
 * ROADMAP item 1b: the click-to-continue affordance shown while
 * Human.nextTrick() blocks on a click. Added to the Handler right before
 * that blocking wait and removed after in a finally, matching BetStepper's
 * add-before/remove-after shape.
 *
 * Deliberately NOT a full-canvas modal like RoundSummaryPanel/GameOverBanner:
 * at this moment the trick's played cards are still on the table (this
 * click is what lets the player see the completed trick before it's
 * cleared), so hiding the board here would defeat the point. Placed instead
 * in a verified-clear horizontal band: with Game.AI_ROW_Y=70 (see that
 * field's own doc -- shifted down from the original 50 to clear the
 * hamburger icon/dropdown now at the very top of the canvas), the AI seat
 * row's lowest text (its score line, baseline y=70+185=255) has its glyph
 * bottom at ~258, and the trump card's top border edge is 305 -- baseline
 * 280 sits with ~9px clearance above the AI row's lowest text and ~21px
 * below to the trump card, and this band is empty of every other on-screen
 * element (AI cards y=100-200, human's played card y=330-430, human's hand
 * y=480-580) regardless of player count/hand size. This ~9px margin above
 * is the tightest clearance in this whole layout -- see Game.AI_ROW_Y's doc
 * for why it can't be pushed any further down.
 *
 * ROADMAP item 10: Rules/Achievements are reached only through the
 * hamburger dropdown now (HamburgerMenu) -- this class no longer renders its
 * own dedicated Rules/Achievements hotspots (removed in the follow-up
 * polish pass that added the dropdown).
 */
public class NextTrickPrompt extends GameObject {
    private static final String TEXT = "Click anywhere to move on to the next trick.";
    private static final int BASELINE_Y = 280;

    // ROADMAP item 10, moved to the very top of the canvas per user feedback:
    // hamburger-menu icon, top-left corner -- duplicated (not shared) across
    // BetStepper/IllegalPlayFeedback/NextTrickPrompt; see BetStepper's
    // HAMBURGER_* fields comment for the full clearance proof and the
    // documented AchievementToast-band trade-off.
    private static final int HAMBURGER_LEFT = 10, HAMBURGER_RIGHT = 40;
    private static final int HAMBURGER_TOP = 5, HAMBURGER_BOTTOM = 35;

    public NextTrickPrompt() {
        InteractionLog.logShown("NextTrickPrompt");
    }

    @Override
    public void tick() {
        //static content -- nothing to update per frame
    }

    @Override
    public void render(Graphics g) {
        renderHamburgerIcon(g);

        Font defaultFont = g.getFont();
        g.setFont(defaultFont.deriveFont(Font.BOLD));
        g.setColor(Color.BLACK);

        FontMetrics metrics = g.getFontMetrics();
        int x = (Game.WIDTH - metrics.stringWidth(TEXT)) / 2;
        g.drawString(TEXT, x, BASELINE_Y);

        g.setFont(defaultFont);
    }

    /**
     * Half-open rect hit-test for the hamburger-menu icon, same convention as
     * BetStepper.isHamburgerHotspot -- see BetStepper's HAMBURGER_* fields
     * comment for this geometry's clearance proof. Rules/Achievements are
     * reached only through the hamburger dropdown now (HamburgerMenu) -- this
     * class no longer has its own dedicated Rules/Achievements hotspots.
     */
    public boolean isHamburgerHotspot(int px, int py) {
        return px >= HAMBURGER_LEFT && px < HAMBURGER_RIGHT && py >= HAMBURGER_TOP && py < HAMBURGER_BOTTOM;
    }

    /**
     * Draws the hamburger icon (three horizontal lines) inside HAMBURGER_*'s
     * bounds -- duplicated identically in BetStepper/IllegalPlayFeedback.
     */
    private static void renderHamburgerIcon(Graphics g) {
        g.setColor(Color.BLACK);
        int lineLeft = HAMBURGER_LEFT + 4;
        int lineRight = HAMBURGER_RIGHT - 4;
        int gap = (HAMBURGER_BOTTOM - HAMBURGER_TOP) / 4;
        for (int i = 1; i <= 3; i++) {
            int lineY = HAMBURGER_TOP + gap * i;
            g.drawLine(lineLeft, lineY, lineRight, lineY);
        }
    }
}
