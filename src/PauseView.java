import java.awt.*;

/**
 * ROADMAP item 10 (hamburger menu, "Pause" item): shows "Paused" plus a
 * "Resume" button (a relabel of RulesView/AchievementsView's own Back-button
 * geometry/position, for visual consistency with this codebase's other
 * full-page views -- same PANEL_X/Y/W/H too).
 *
 * Judgment call, documented here rather than silently assumed: this view
 * *is* the whole freeze. "Pause" is only ever reachable from inside one of
 * Human's three blocking click-loops (bet/playCard/nextTrick), which means
 * the game-logic thread is already parked on mouseInput.awaitClick() at the
 * moment a player could possibly open this menu -- there is no separate
 * AI-turn timer or in-progress animation running concurrently that would
 * keep advancing while this view sits on screen (AI turns run to completion
 * synchronously within Trick.play()'s loop, never through a blocking click
 * wait). So simply showing this view and blocking on its own click loop
 * (same shape as RulesView.showBlocking) *is* pausing the game -- nothing
 * else needs to be separately suspended. If a future change ever introduces
 * a real timer/animation independent of the click-loop (e.g. an AI "thinking"
 * delay), this reasoning would need revisiting.
 */
public class PauseView extends GameObject {
    private static final int PANEL_X = 40, PANEL_Y = 20, PANEL_W = 760, PANEL_H = 590;
    private static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    private static final int TITLE_Y = 300;

    private static final int RESUME_TOP = 576, RESUME_BOTTOM = 602;
    private static final int RESUME_LEFT = 680, RESUME_RIGHT = 760;

    /**
     * Same lifecycle shape as RulesView.showBlocking, including the same
     * ACHIEVEMENTTOAST click-to-dismiss check ahead of the Resume check.
     */
    public static void showBlocking(Handler handler, MouseInput mouseInput, AchievementToast achievementToast) {
        PauseView view = new PauseView();
        handler.addObject(view);
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    achievementToast.dismiss();
                    continue;
                }
                if (view.isResumeButton(click.x, click.y)) {
                    return;
                }
            }
        } finally {
            handler.removeObject(view);
        }
    }

    /** Half-open rect hit-test, same convention/geometry as RulesView.isBackButton (relabeled Resume here). */
    public boolean isResumeButton(int px, int py) {
        return px >= RESUME_LEFT && px < RESUME_RIGHT && py >= RESUME_TOP && py < RESUME_BOTTOM;
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
        drawCentered(g, "Paused", TITLE_Y);

        g.setFont(defaultFont);
        g.drawRect(RESUME_LEFT, RESUME_TOP, RESUME_RIGHT - RESUME_LEFT - 1, RESUME_BOTTOM - RESUME_TOP - 1);
        drawCenteredIn(g, "Resume", RESUME_LEFT, RESUME_RIGHT, RESUME_BOTTOM - 8);

        g.setFont(defaultFont);
    }

    /** Draws TEXT horizontally centered within CONTENT_LEFT..CONTENT_RIGHT, same helper shape as RulesView's private copy. */
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
