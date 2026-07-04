import java.awt.*;
import java.util.List;

/**
 * ROADMAP item 1c: the end-of-game outcome banner. Added to the Handler once
 * per game and removed after Game.awaitPlayAgain() detects a click on the
 * Play Again hotspot (see Game.play()'s
 * showGameOverBanner/awaitPlayAgain/removeObject/restartForNewGame
 * sequence) -- the same add-before/blocking-click-loop/remove-after shape
 * used by RulesView, BetStepper, and NextTrickPrompt.
 *
 * STANDINGS must already be sorted descending by score (stable sort, ties
 * keep original/seat order) by the caller -- see Game.showGameOverBanner.
 *
 * Known inherited quirk, not fixed here: Game.determineWinner() only
 * replaces the winner on strictly-greater score, so a true tie leaves
 * whichever player appears earliest in getPlayers() as "the winner." This
 * banner reuses that result directly rather than re-deriving/fixing it.
 */
public class GameOverBanner extends ModalOverlay {
    private static final int TITLE_Y = 150;
    private static final int SUBTITLE_Y = 185;
    private static final int HEADER_Y = 215;
    private static final int DIVIDER_Y = 225;
    private static final int FIRST_ROW_Y = 250;
    private static final int ROW_PITCH = 26;
    private static final int FOOTER_Y = 430;

    /**
     * ROADMAP item 1: the "Play Again" button, placed in the gap below the
     * footer (~430->530, the panel's bottom border) rather than sandwiched
     * between the footer and standings -- see this item's completion report
     * for the pixel audit this was checked against. Height (34px) mirrors
     * StartScreen's RULES/START button height for visual consistency;
     * baseline convention (BOTTOM - 10) also mirrors StartScreen's
     * drawCenteredIn. "Play Again" measures 57px wide in the default font
     * (Dialog, plain, 12) via a headless FontMetrics check -- see
     * TestGameOverBanner -- comfortably inside this 160px-wide box.
     */
    private static final String PLAY_AGAIN_LABEL = "Play Again";
    private static final int PLAY_AGAIN_TOP = 460, PLAY_AGAIN_BOTTOM = 494;
    private static final int PLAY_AGAIN_LEFT = 340, PLAY_AGAIN_RIGHT = 500;

    private static final int NAME_X = 130;
    private static final int SCORE_X = 500;

    private final String winnerName;
    private final int winnerScore;
    private final boolean humanWon;
    private final List<Player> standings;

    public GameOverBanner(Player winner, boolean humanWon, List<Player> standings) {
        this.winnerName = winner.getName();
        this.winnerScore = winner.getScore();
        this.humanWon = humanWon;
        this.standings = standings;
    }

    @Override
    protected void renderContent(Graphics g) {
        Font defaultFont = g.getFont();

        g.setFont(TITLE_FONT);
        if (humanWon) {
            g.setColor(GOLD);
            drawCentered(g, "You Win!", TITLE_Y);
        } else {
            g.setColor(Color.BLACK);
            drawCentered(g, "You Lose", TITLE_Y);
        }

        g.setFont(defaultFont);
        g.setColor(Color.BLACK);
        String subtitle = humanWon
                ? "Final standings:"
                : winnerName + " wins with " + winnerScore + " points. Final standings:";
        drawCentered(g, subtitle, SUBTITLE_Y);

        g.setFont(HEADER_FONT);
        g.drawString("Player", NAME_X, HEADER_Y);
        g.drawString("Final Score", SCORE_X, HEADER_Y);

        g.setFont(defaultFont);
        g.drawLine(CONTENT_LEFT, DIVIDER_Y, CONTENT_RIGHT, DIVIDER_Y);

        g.setColor(Color.BLACK);
        int y = FIRST_ROW_Y;
        int rank = 1;
        for (Player player : standings) {
            String label = rank + ". " + player.getName() + (player.getID() == ID.HUMAN ? " (you)" : "");
            g.drawString(label, NAME_X, y);
            g.drawString(String.valueOf(player.getScore()), SCORE_X, y);
            y += ROW_PITCH;
            rank++;
        }

        drawCentered(g, "Game Over -- close this window to exit.", FOOTER_Y);

        g.setColor(Color.BLACK);
        g.drawRect(PLAY_AGAIN_LEFT, PLAY_AGAIN_TOP,
                PLAY_AGAIN_RIGHT - PLAY_AGAIN_LEFT - 1, PLAY_AGAIN_BOTTOM - PLAY_AGAIN_TOP - 1);
        drawCenteredIn(g, PLAY_AGAIN_LABEL, PLAY_AGAIN_LEFT, PLAY_AGAIN_RIGHT, PLAY_AGAIN_BOTTOM - 10);
    }

    /** Draws TEXT horizontally centered within [left, right), same helper shape as RulesView/StartScreen's private copies. */
    private static void drawCenteredIn(Graphics g, String text, int left, int right, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int x = left + ((right - left) - width) / 2;
        g.drawString(text, x, y);
    }

    /**
     * Half-open rect hit-test, same convention as
     * NextTrickPrompt.isRulesHotspot/RulesView.isBackButton. Called by
     * Game.awaitPlayAgain (backend pass) after showGameOverBanner returns
     * this instance.
     */
    public boolean isPlayAgainHotspot(int px, int py) {
        return px >= PLAY_AGAIN_LEFT && px < PLAY_AGAIN_RIGHT
                && py >= PLAY_AGAIN_TOP && py < PLAY_AGAIN_BOTTOM;
    }
}
