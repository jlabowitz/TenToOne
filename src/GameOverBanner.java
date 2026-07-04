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

    /**
     * ROADMAP item 2: two fixed line slots reserved between the subtitle and
     * the standings header for the new high-score/win-streak lines -- always
     * reserved, whether or not either line actually has content that round
     * (same "reserve a fixed slot regardless of fill" convention RulesView's
     * LEGEND_TOP/BOTTOM already established), so this doesn't need dynamic
     * layout math. Everything below HEADER_Y shifted down accordingly from
     * this item's pre-existing values (215/225/250) to make room.
     */
    private static final int NEW_HIGH_SCORE_Y = 202;
    private static final int STREAK_LINE_Y = 220;

    private static final int HEADER_Y = 244;
    private static final int DIVIDER_Y = 254;
    private static final int FIRST_ROW_Y = 278;
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
    private final boolean newHighScore;
    private final int streakToReport;

    /**
     * ROADMAP item 2: newHighScore/streakToReport are pre-computed by the
     * caller (Game, right after AchievementEngine.checkGameEnd updates
     * SaveData) rather than derived here -- this class only renders what
     * it's given. streakToReport is the *new* current win streak on a win,
     * or the streak that just ended (0 if there wasn't one) on a loss -- see
     * winStreakLine's own doc.
     */
    public GameOverBanner(Player winner, boolean humanWon, List<Player> standings,
                           boolean newHighScore, int streakToReport) {
        this.winnerName = winner.getName();
        this.winnerScore = winner.getScore();
        this.humanWon = humanWon;
        this.standings = standings;
        this.newHighScore = newHighScore;
        this.streakToReport = streakToReport;
    }

    /**
     * Pure text-computation helper, testable without a Graphics context.
     * humanWon determines phrasing; streakToReport is the new current streak
     * on a win, or the streak that just ended on a loss. Returns null (no
     * line at all) on a loss with no prior streak (streakToReport == 0) --
     * there's nothing meaningful to report ("Streak ended at 0" would read
     * oddly for a streak that never existed).
     */
    static String winStreakLine(boolean humanWon, int streakToReport) {
        if (humanWon) {
            return streakToReport + "-game win streak!";
        }
        if (streakToReport > 0) {
            return "Streak ended at " + streakToReport + ".";
        }
        return null;
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

        if (newHighScore) {
            g.setFont(defaultFont.deriveFont(Font.BOLD));
            g.setColor(GOLD);
            drawCentered(g, "New High Score!", NEW_HIGH_SCORE_Y);
        }

        String streakLine = winStreakLine(humanWon, streakToReport);
        if (streakLine != null) {
            g.setFont(defaultFont);
            g.setColor(Color.BLACK);
            drawCentered(g, streakLine, STREAK_LINE_Y);
        }

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
