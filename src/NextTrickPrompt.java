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
 * in a verified-clear horizontal band: AI text's lowest glyph bottom is
 * ~239, the trump card's top border edge is 305 -- baseline 280 sits with
 * ~12px clearance above and ~21px below, and this band is empty of every
 * other on-screen element (AI cards y=80-180, human's played card
 * y=330-430, human's hand y=480-580) regardless of player count/hand size.
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

    @Override
    public void tick() {
        //static content -- nothing to update per frame
    }

    @Override
    public void render(Graphics g) {
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
    }

    /**
     * Half-open rect hit-test, same convention as BetStepper.controlAt.
     * Named isRulesHotspot (not controlAt) since this GameObject has only
     * one clickable extra control, not a small fixed enum of them.
     */
    public boolean isRulesHotspot(int px, int py) {
        return px >= RULES_LEFT && px < RULES_RIGHT && py >= RULES_TOP && py < RULES_BOTTOM;
    }
}
