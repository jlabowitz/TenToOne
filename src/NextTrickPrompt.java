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
 * ROADMAP item 1: also renders a small "Rules" hotspot in the same
 * verified-clear band, top-right of it and away from the centered prompt
 * text, so RulesView.showBlocking() can be reached mid-game from
 * Human.nextTrick() (wired up by the backend pass, not this class). This
 * hotspot's horizontal slot (x=760-820) is a design-time estimate, not
 * pixel-verified live -- flagged in this item's completion report; it can
 * only be exercised in the running game once the backend pass wires
 * Human.nextTrick() to check isRulesHotspot().
 */
public class NextTrickPrompt extends GameObject {
    private static final String TEXT = "Click anywhere to move on to the next trick.";
    private static final int BASELINE_Y = 280;
    private static final String RULES_LABEL = "Rules";
    private static final int RULES_TOP = 265, RULES_BOTTOM = 295;
    private static final int RULES_LEFT = 760, RULES_RIGHT = 820;

    // ROADMAP follow-up: in-game Achievements hotspot, mirroring the Rules
    // hotspot above -- same y band, sitting to its left with a visible gap.
    // Left edge (600) is measured against this class's own centered TEXT
    // ("Click anywhere to move on to the next trick."), which ends at x=543
    // via headless FontMetrics measurement (bold default font, centered on
    // Game.WIDTH=840) -- 600 leaves a 57px gap from that, and 730 leaves a
    // 30px gap before RULES_LEFT (760). Sized wider (130px) than the Rules
    // box (60px) since "Achievements" (12 chars) is much longer than "Rules"
    // (5) -- see TestNextTrickPrompt's non-collision and text-clearance
    // tests for the geometry proof.
    private static final String ACHIEVEMENTS_LABEL = "Achievements";
    private static final int ACHIEVEMENTS_TOP = 265, ACHIEVEMENTS_BOTTOM = 295;
    private static final int ACHIEVEMENTS_LEFT = 600, ACHIEVEMENTS_RIGHT = 730;

    // ROADMAP item 10, moved to the very top of the canvas per user feedback:
    // hamburger-menu icon, top-left corner -- duplicated (not shared) across
    // BetStepper/IllegalPlayFeedback/NextTrickPrompt; see BetStepper's
    // HAMBURGER_* fields comment for the full clearance proof and the
    // documented AchievementToast-band trade-off.
    private static final int HAMBURGER_LEFT = 10, HAMBURGER_RIGHT = 40;
    private static final int HAMBURGER_TOP = 5, HAMBURGER_BOTTOM = 35;

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
        g.setColor(Color.BLACK);
        g.drawRect(RULES_LEFT, RULES_TOP, RULES_RIGHT - RULES_LEFT - 1, RULES_BOTTOM - RULES_TOP - 1);
        FontMetrics defaultMetrics = g.getFontMetrics();
        int labelWidth = defaultMetrics.stringWidth(RULES_LABEL);
        int labelX = RULES_LEFT + ((RULES_RIGHT - RULES_LEFT) - labelWidth) / 2;
        g.drawString(RULES_LABEL, labelX, RULES_BOTTOM - 10);

        g.drawRect(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT - ACHIEVEMENTS_LEFT - 1, ACHIEVEMENTS_BOTTOM - ACHIEVEMENTS_TOP - 1);
        int achievementsLabelWidth = defaultMetrics.stringWidth(ACHIEVEMENTS_LABEL);
        int achievementsLabelX = ACHIEVEMENTS_LEFT + ((ACHIEVEMENTS_RIGHT - ACHIEVEMENTS_LEFT) - achievementsLabelWidth) / 2;
        g.drawString(ACHIEVEMENTS_LABEL, achievementsLabelX, ACHIEVEMENTS_BOTTOM - 10);
    }

    /**
     * Half-open rect hit-test, same convention as BetStepper.controlAt.
     * Named isRulesHotspot (not controlAt) since this GameObject has only
     * one clickable extra control, not a small fixed enum of them.
     */
    public boolean isRulesHotspot(int px, int py) {
        return px >= RULES_LEFT && px < RULES_RIGHT && py >= RULES_TOP && py < RULES_BOTTOM;
    }

    /**
     * Half-open rect hit-test for the Achievements hotspot, same convention
     * as isRulesHotspot. See the ACHIEVEMENTS_* fields' comment for how this
     * geometry was chosen clear of the Rules box and the centered prompt
     * text.
     */
    public boolean isAchievementsHotspot(int px, int py) {
        return px >= ACHIEVEMENTS_LEFT && px < ACHIEVEMENTS_RIGHT && py >= ACHIEVEMENTS_TOP && py < ACHIEVEMENTS_BOTTOM;
    }

    /**
     * Half-open rect hit-test for the hamburger-menu icon, same convention as
     * isRulesHotspot/isAchievementsHotspot -- see BetStepper's HAMBURGER_*
     * fields comment for this geometry's clearance proof.
     */
    public boolean isHamburgerHotspot(int px, int py) {
        return px >= HAMBURGER_LEFT && px < HAMBURGER_RIGHT && py >= HAMBURGER_TOP && py < HAMBURGER_BOTTOM;
    }

    /**
     * Draws the hamburger icon (three horizontal lines) inside HAMBURGER_*'s
     * bounds -- duplicated identically in BetStepper/IllegalPlayFeedback, same
     * convention as this class's own Rules/Achievements box rendering.
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
