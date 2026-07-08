import java.awt.*;

/**
 * ROADMAP item 10 follow-up: the new Stats modal, reachable from the Start
 * Screen's new Stats button (replacing the old inline "Best score / best win
 * streak / games played" text line). Same lifecycle/geometry shape as
 * RulesView/AchievementsView/SettingsView -- a full 840x630 canvas
 * GameObject, added to the Handler, blocking click loop awaiting Back,
 * removed via showBlocking in a finally, click-outside-dismiss via the
 * shared ModalDismiss helper -- see RulesView's class doc for why these
 * views don't extend ModalOverlay.
 *
 * Reads the live SaveData directly (same as AchievementsView already does)
 * rather than having StartScreen thread every individual stat field through
 * its own constructor -- this class is the only place that needs to know
 * SaveData's full stat shape.
 *
 * Content grouped logically (games, then scoring, then streaks/betting)
 * rather than in field-declaration order: games played/won/win% together,
 * then total points/average score/high score, then current/best win streak
 * and bet-hit rate. All percentage/average derivations guard divide-by-zero on
 * gamesPlayed/totalRoundsBet even though this view should never actually
 * render with gamesPlayed == 0 in practice (StartScreen hides the Stats
 * button entirely in that case, mirroring Resume's own gating) -- defending
 * here anyway rather than relying on that caller-side gate.
 *
 * Sized modestly (PANEL_H=358) to match this page's sparse content, same
 * "shrink to fit, don't preserve a big panel just in case" judgment call
 * SettingsView's own resize in this same pass made.
 */
public class StatsView extends GameObject {
    private static final int PANEL_X = 40, PANEL_Y = 20, PANEL_W = 760, PANEL_H = 358;
    private static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    private static final int TITLE_Y = 65;

    private static final int GAMES_HEADER_Y = 100;
    private static final int GAMES_PLAYED_Y = 120;
    private static final int GAMES_WON_Y = 138;
    private static final int WIN_RATE_Y = 156;

    private static final int SCORING_HEADER_Y = 186;
    private static final int TOTAL_POINTS_Y = 206;
    private static final int AVERAGE_SCORE_Y = 224;
    private static final int HIGH_SCORE_Y = 242;

    private static final int STREAKS_HEADER_Y = 272;
    private static final int CURRENT_STREAK_Y = 292;
    private static final int BEST_STREAK_Y = 310;
    private static final int BET_HIT_RATE_Y = 328;

    private static final int BACK_TOP = 344, BACK_BOTTOM = 370;
    private static final int BACK_LEFT = 680, BACK_RIGHT = 760;

    private final SaveData saveData;

    public StatsView(SaveData saveData) {
        this.saveData = saveData;
    }

    /** Win rate as a whole-number percentage, 0 when no games have been played yet -- guards divide-by-zero. */
    public static int winPercent(SaveData data) {
        return data.gamesPlayed == 0 ? 0 : Math.round(100f * data.gamesWon / data.gamesPlayed);
    }

    /** Average final score per completed game, 0.0 when no games have been played yet -- guards divide-by-zero. */
    public static double averageScorePerGame(SaveData data) {
        return data.gamesPlayed == 0 ? 0.0 : (double) data.totalPoints / data.gamesPlayed;
    }

    /** "Hit your bet" rate as a whole-number percentage of rounds bet in, 0 when no rounds have been bet in yet -- guards divide-by-zero. */
    public static int betHitPercent(SaveData data) {
        return data.totalRoundsBet == 0 ? 0 : Math.round(100f * data.totalRoundsBetHit / data.totalRoundsBet);
    }

    /**
     * Same lifecycle shape as SettingsView.showBlocking, including the same
     * ACHIEVEMENTTOAST click-to-dismiss check ahead of the Back/outside-panel
     * checks (code-review Finding 1's convention, applied consistently to
     * every showBlocking view in this codebase).
     */
    public static void showBlocking(Handler handler, MouseInput mouseInput, AchievementToast achievementToast, SaveData saveData) {
        StatsView view = new StatsView(saveData);
        handler.addObject(view);
        InteractionLog.logShown("StatsView");
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (view.isBackButton(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "StatsView.Back");
                    return;
                }
                if (ModalDismiss.isOutsidePanel(click, view::isInsidePanel, "StatsView")) {
                    return;
                }
                InteractionLog.logClick(click.x, click.y, "no control matched (StatsView)");
            }
        } finally {
            handler.removeObject(view);
        }
    }

    /** Half-open rect hit-test, same convention as RulesView.isBackButton. */
    public boolean isBackButton(int px, int py) {
        return px >= BACK_LEFT && px < BACK_RIGHT && py >= BACK_TOP && py < BACK_BOTTOM;
    }

    /** Half-open rect hit-test for the panel itself, used to distinguish an inert click on the panel from a true miss-click (see ModalDismiss). */
    public boolean isInsidePanel(int px, int py) {
        return px >= PANEL_X && px < PANEL_X + PANEL_W && py >= PANEL_Y && py < PANEL_Y + PANEL_H;
    }

    @Override
    public void tick() {
        //static content -- nothing to update per frame
    }

    @Override
    public void render(Graphics g) {
        Font defaultFont = g.getFont();

        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);

        g.setColor(Color.WHITE);
        g.fillRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        g.setColor(Color.BLACK);
        g.drawRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        g.setFont(ModalOverlay.TITLE_FONT);
        g.setColor(Color.BLACK);
        drawCentered(g, "Your Stats", TITLE_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("Games", CONTENT_LEFT, GAMES_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("Games played: " + saveData.gamesPlayed, CONTENT_LEFT, GAMES_PLAYED_Y);
        g.drawString("Games won: " + saveData.gamesWon, CONTENT_LEFT, GAMES_WON_Y);
        g.drawString("Win rate: " + winPercent(saveData) + "%", CONTENT_LEFT, WIN_RATE_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("Scoring", CONTENT_LEFT, SCORING_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("Total points scored: " + saveData.totalPoints, CONTENT_LEFT, TOTAL_POINTS_Y);
        g.drawString(String.format("Average score per game: %.1f", averageScorePerGame(saveData)), CONTENT_LEFT, AVERAGE_SCORE_Y);
        g.drawString("High score: " + saveData.highScore, CONTENT_LEFT, HIGH_SCORE_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("Streaks & Betting", CONTENT_LEFT, STREAKS_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("Current win streak: " + saveData.currentWinStreak, CONTENT_LEFT, CURRENT_STREAK_Y);
        g.drawString("Best win streak: " + saveData.bestWinStreakEver, CONTENT_LEFT, BEST_STREAK_Y);
        g.drawString("Bet-hit rate: " + betHitPercent(saveData) + "% (" + saveData.totalRoundsBetHit + "/" + saveData.totalRoundsBet + " rounds)",
                CONTENT_LEFT, BET_HIT_RATE_Y);

        g.setColor(Color.BLACK);
        g.drawRect(BACK_LEFT, BACK_TOP, BACK_RIGHT - BACK_LEFT - 1, BACK_BOTTOM - BACK_TOP - 1);
        drawCenteredIn(g, "Back", BACK_LEFT, BACK_RIGHT, BACK_BOTTOM - 8);

        g.setFont(defaultFont);
    }

    /** Draws TEXT horizontally centered within CONTENT_LEFT..CONTENT_RIGHT. */
    private static void drawCentered(Graphics g, String text, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int x = CONTENT_LEFT + ((CONTENT_RIGHT - CONTENT_LEFT) - width) / 2;
        g.drawString(text, x, y);
    }

    /** Draws TEXT horizontally centered within [left, right). */
    private static void drawCenteredIn(Graphics g, String text, int left, int right, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int x = left + ((right - left) - width) / 2;
        g.drawString(text, x, y);
    }
}
