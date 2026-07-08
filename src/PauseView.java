import java.awt.*;

/**
 * ROADMAP item 10 (hamburger menu, "Pause" item): shows "Paused" plus a
 * "Resume" button. As of this follow-up polish pass, a small dialog centered
 * on the 840x630 canvas (300x160) rather than the earlier full-canvas panel
 * -- still draws the same full-canvas light-gray scrim behind it (unchanged),
 * only the white panel itself shrunk.
 *
 * Also click-outside-dismisses now, mirroring HamburgerMenu's own
 * miss-click convention: a click anywhere outside the panel resumes with no
 * other action, same as clicking Resume itself -- the earlier Resume-only
 * dismiss meant a miss-click just looped forever.
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
    private static final int PANEL_W = 300, PANEL_H = 160;
    private static final int PANEL_X = (Game.WIDTH - PANEL_W) / 2;
    private static final int PANEL_Y = (Game.HEIGHT - PANEL_H) / 2;
    private static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    private static final int TITLE_Y = PANEL_Y + 55;

    /**
     * ROADMAP item 10 follow-up bug fix: previously PANEL_X+60/+180 (a 120px
     * button starting 60px from the panel's left edge) -- NOT centered on the
     * panel despite looking like it should be, since the title above it *is*
     * centered on the panel's full width via drawCentered's CONTENT_LEFT/
     * RIGHT (symmetric 30px margins around PANEL_X..PANEL_X+PANEL_W). A
     * 120px-wide button centered on a 300px-wide panel needs to start
     * (300-120)/2=90px in from the left edge, not 60 -- fixed here.
     */
    private static final int RESUME_LEFT = PANEL_X + 90, RESUME_RIGHT = PANEL_X + 210;
    private static final int RESUME_BOTTOM = PANEL_Y + PANEL_H - 20, RESUME_TOP = RESUME_BOTTOM - 36;

    /**
     * Same lifecycle shape as RulesView.showBlocking, including the same
     * ACHIEVEMENTTOAST click-to-dismiss check ahead of the Resume check.
     * A click outside the panel resumes with no other action, matching
     * HamburgerMenu's own miss-click convention -- unlike RulesView/
     * AchievementsView's full-canvas panels (where every click is "inside"
     * something), this panel is small enough that a miss-click is common
     * and must not hang the loop forever.
     */
    public static void showBlocking(Handler handler, MouseInput mouseInput, AchievementToast achievementToast) {
        PauseView view = new PauseView();
        handler.addObject(view);
        InteractionLog.logShown("PauseView");
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (view.isResumeButton(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "PauseView.Resume");
                    return;
                }
                if (ModalDismiss.isOutsidePanel(click, view::isInsidePanel, "PauseView")) {
                    return;
                }
                InteractionLog.logClick(click.x, click.y, "no control matched (PauseView)");
            }
        } finally {
            handler.removeObject(view);
        }
    }

    /** Half-open rect hit-test, same convention/geometry as RulesView.isBackButton (relabeled Resume here). */
    public boolean isResumeButton(int px, int py) {
        return px >= RESUME_LEFT && px < RESUME_RIGHT && py >= RESUME_TOP && py < RESUME_BOTTOM;
    }

    /** Half-open rect hit-test for the panel itself, used to distinguish an inert click on the panel from a true miss-click. */
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
