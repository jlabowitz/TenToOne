import java.awt.*;
import java.util.List;

/**
 * ROADMAP item 1c: the end-of-game outcome banner. Added to the Handler once
 * and never removed -- the render thread keeps drawing the final frame
 * forever after Game.play() returns, so no click-gate/dismiss logic exists
 * here (unlike RoundSummaryPanel/NextTrickPrompt).
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
    }
}
