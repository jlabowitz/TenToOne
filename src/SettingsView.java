import java.awt.*;

/**
 * ROADMAP item 10 (hamburger menu, "Settings" item): a single labeled
 * checkbox for GameSettings.totalBetsCannotEqualTricks. Same lifecycle/
 * geometry shape as RulesView/AchievementsView (full 840x630 canvas
 * GameObject, same PANEL_X/Y/W/H and Back button geometry, added to the
 * Handler, blocking click loop awaiting Back, removed via showBlocking in a
 * finally) -- see RulesView's class doc for why these views don't extend
 * ModalOverlay.
 *
 * ROADMAP item 10 follow-up: clicking the checkbox mutates
 * gameSettings.pendingTotalBetsCannotEqualTricks, NOT the live
 * totalBetsCannotEqualTricks field gameplay actually reads -- Round.bet()
 * reads the live field fresh for every bettor, every round, so mutating it
 * directly here would let a mid-round Settings visit change the rule for the
 * very next bettor in the *same* round (confusing, and the exact risk the
 * user flagged). Game.java's applyPending() call sites (restartForNewGame(),
 * establishFreshGameState()) are what actually copy the staged value onto the
 * live one, only at the start of a genuinely fresh game -- see GameSettings'
 * own class doc. This view renders the *pending* value's checked state, so
 * reopening Settings before a new game starts still shows what you last
 * chose (not the live value your current game is running with).
 *
 * ROADMAP item 10 follow-up: two more changes -- (1) a click outside the
 * panel now dismisses (with no other action), via the shared ModalDismiss
 * helper (see that class's doc); (2) user feedback was "way too big" for
 * this page's sparse single-checkbox content, so PANEL_H shrank
 * substantially (590 -> 314) rather than being trimmed to the theoretical
 * minimum around today's one toggle -- the user mentioned upcoming
 * visual-effects settings, so there's deliberately ~120px of blank room
 * between NOTE_Y and Back left for 1-2 more toggle-style rows before this
 * needs resizing again.
 */
public class SettingsView extends GameObject {
    private static final int PANEL_X = 40, PANEL_Y = 20, PANEL_W = 760, PANEL_H = 314;
    private static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    private static final int TITLE_Y = 60;

    private static final int LABEL_Y = 140;
    private static final int TOGGLE_TOP = 120, TOGGLE_BOTTOM = 154;
    private static final int TOGGLE_LEFT = 500, TOGGLE_RIGHT = 600;

    /** The actual small checkbox square drawn/checked inside the larger TOGGLE_* clickable hotspot -- keeps a generous touch target without a giant checkbox. */
    private static final int CHECKBOX_SIZE = 20;
    private static final int CHECKBOX_LEFT = TOGGLE_LEFT;
    private static final int CHECKBOX_TOP = TOGGLE_TOP + (TOGGLE_BOTTOM - TOGGLE_TOP - CHECKBOX_SIZE) / 2;

    private static final int NOTE_Y = TOGGLE_BOTTOM + 26;

    private static final int BACK_TOP = 300, BACK_BOTTOM = 326;
    private static final int BACK_LEFT = 680, BACK_RIGHT = 760;

    private final GameSettings gameSettings;

    public SettingsView(GameSettings gameSettings) {
        this.gameSettings = gameSettings;
    }

    /**
     * Same lifecycle shape as RulesView.showBlocking, including the same
     * ACHIEVEMENTTOAST click-to-dismiss check ahead of the toggle/Back
     * checks (code-review Finding 1's convention, applied consistently to
     * every showBlocking view in this codebase).
     */
    public static void showBlocking(Handler handler, MouseInput mouseInput, AchievementToast achievementToast,
                                     GameSettings gameSettings) {
        SettingsView view = new SettingsView(gameSettings);
        handler.addObject(view);
        InteractionLog.logShown("SettingsView");
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (view.isToggleHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "SettingsView.Toggle");
                    gameSettings.pendingTotalBetsCannotEqualTricks = !gameSettings.pendingTotalBetsCannotEqualTricks;
                    continue;
                }
                if (view.isBackButton(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "SettingsView.Back");
                    return;
                }
                if (ModalDismiss.isOutsidePanel(click, view::isInsidePanel, "SettingsView")) {
                    return;
                }
                InteractionLog.logClick(click.x, click.y, "no control matched (SettingsView)");
            }
        } finally {
            handler.removeObject(view);
        }
    }

    /** Half-open rect hit-test, same convention as RulesView.isBackButton. */
    public boolean isBackButton(int px, int py) {
        return px >= BACK_LEFT && px < BACK_RIGHT && py >= BACK_TOP && py < BACK_BOTTOM;
    }

    /** Half-open rect hit-test for the checkbox toggle -- deliberately larger than the drawn checkbox square itself, for a generous touch target. */
    public boolean isToggleHotspot(int px, int py) {
        return px >= TOGGLE_LEFT && px < TOGGLE_RIGHT && py >= TOGGLE_TOP && py < TOGGLE_BOTTOM;
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
        drawCentered(g, "Settings", TITLE_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("Total bets cannot equal number of tricks", CONTENT_LEFT, LABEL_Y);

        g.setFont(defaultFont);
        g.setColor(Color.BLACK);
        g.drawRect(CHECKBOX_LEFT, CHECKBOX_TOP, CHECKBOX_SIZE - 1, CHECKBOX_SIZE - 1);
        if (gameSettings.pendingTotalBetsCannotEqualTricks) {
            // simple checkmark: two short segments, not a filled square, so
            // the empty-box state is unambiguous at a glance
            g.drawLine(CHECKBOX_LEFT + 4, CHECKBOX_TOP + 11, CHECKBOX_LEFT + 8, CHECKBOX_TOP + 15);
            g.drawLine(CHECKBOX_LEFT + 8, CHECKBOX_TOP + 15, CHECKBOX_LEFT + 16, CHECKBOX_TOP + 4);
        }

        g.setColor(Color.DARK_GRAY);
        g.drawString("Changes apply starting your next game.", CONTENT_LEFT, NOTE_Y);

        g.setColor(Color.BLACK);
        g.drawRect(BACK_LEFT, BACK_TOP, BACK_RIGHT - BACK_LEFT - 1, BACK_BOTTOM - BACK_TOP - 1);
        drawCenteredIn(g, "Back", BACK_LEFT, BACK_RIGHT, BACK_BOTTOM - 8);

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
