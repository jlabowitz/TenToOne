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
 */
public class NextTrickPrompt extends GameObject {
    private static final String TEXT = "Click anywhere to move on to the next trick.";
    private static final int BASELINE_Y = 280;

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
    }
}
