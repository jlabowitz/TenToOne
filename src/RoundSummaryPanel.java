import java.awt.*;
import java.util.List;

/**
 * ROADMAP item 1a: the round-transition/score-summary modal shown after a
 * round's scores have been adjusted. Added to the Handler last (see
 * ModalOverlay's class doc) and removed on any click, mirroring
 * BetStepper's add-before/remove-after lifecycle.
 *
 * ROWS must already have totalAfter filled in (see Game.applyTotals) by the
 * time this is constructed -- this class only renders, it doesn't compute.
 */
public class RoundSummaryPanel extends ModalOverlay {
    private static final int TITLE_Y = 150;
    private static final int HEADER_Y = 190;
    private static final int DIVIDER_Y = 200;
    private static final int FIRST_ROW_Y = 225;
    private static final int ROW_PITCH = 26;
    private static final int PROMPT_Y = 410;

    private static final int NAME_X = 130;
    private static final int BET_X = 310;
    private static final int TRICKS_X = 380;
    private static final int DELTA_X = 460;
    private static final int TOTAL_X = 630;

    private final int roundIndex;
    private final List<RoundResultRow> rows;

    public RoundSummaryPanel(int roundIndex, List<RoundResultRow> rows) {
        this.roundIndex = roundIndex;
        this.rows = rows;
    }

    @Override
    protected void renderContent(Graphics g) {
        Font defaultFont = g.getFont();

        g.setFont(TITLE_FONT);
        g.setColor(Color.BLACK);
        drawCentered(g, "Round " + (roundIndex + 1) + " Complete", TITLE_Y);

        g.setFont(HEADER_FONT);
        g.drawString("Player", NAME_X, HEADER_Y);
        g.drawString("Bet", BET_X, HEADER_Y);
        g.drawString("Tricks", TRICKS_X, HEADER_Y);
        g.drawString("Round Delta", DELTA_X, HEADER_Y);
        g.drawString("Total", TOTAL_X, HEADER_Y);

        g.setFont(defaultFont);
        g.drawLine(CONTENT_LEFT, DIVIDER_Y, CONTENT_RIGHT, DIVIDER_Y);

        int y = FIRST_ROW_Y;
        for (RoundResultRow row : rows) {
            g.setColor(Color.BLACK);
            String name = row.name + (row.isHuman ? " (you)" : "");
            g.drawString(name, NAME_X, y);
            g.drawString(String.valueOf(row.bet), BET_X, y);
            g.drawString(String.valueOf(row.tricksTaken), TRICKS_X, y);

            //word + color, not color alone -- matches the
            //Suit.getDisplayName()/getColor() convention elsewhere
            String deltaText = "+" + row.roundDelta + (row.bonusHit ? " (bonus!)" : "");
            g.setColor(row.bonusHit ? GOLD : Color.BLACK);
            g.drawString(deltaText, DELTA_X, y);

            g.setColor(Color.BLACK);
            g.drawString(String.valueOf(row.totalAfter), TOTAL_X, y);

            y += ROW_PITCH;
        }

        g.setColor(Color.BLACK);
        drawCentered(g, "Click anywhere to continue", PROMPT_Y);
    }
}
