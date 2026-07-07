import java.awt.*;

/**
 * ROADMAP item 10 (hamburger menu, "Settings" item): a single labeled ON/OFF
 * toggle for GameSettings.totalBetsCannotEqualTricks. Same lifecycle/geometry
 * shape as RulesView/AchievementsView (full 840x630 canvas GameObject, same
 * PANEL_X/Y/W/H and Back button geometry, added to the Handler, blocking
 * click loop awaiting Back, removed via showBlocking in a finally) -- see
 * RulesView's class doc for why these views don't extend ModalOverlay.
 *
 * GameSettings is a plain public-field holder with no getters/setters (see
 * its own class doc) -- clicking the toggle mutates the live instance's
 * field directly, same as any other direct-field-access call site already in
 * this codebase (e.g. Round.bet() reading gameSettings.totalBetsCannotEqualTricks).
 */
public class SettingsView extends GameObject {
    private static final int PANEL_X = 40, PANEL_Y = 20, PANEL_W = 760, PANEL_H = 590;
    private static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    private static final int TITLE_Y = 60;

    private static final int LABEL_Y = 140;
    private static final int TOGGLE_TOP = 120, TOGGLE_BOTTOM = 154;
    private static final int TOGGLE_LEFT = 500, TOGGLE_RIGHT = 600;

    private static final int BACK_TOP = 576, BACK_BOTTOM = 602;
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
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    achievementToast.dismiss();
                    continue;
                }
                if (view.isToggleHotspot(click.x, click.y)) {
                    gameSettings.totalBetsCannotEqualTricks = !gameSettings.totalBetsCannotEqualTricks;
                    continue;
                }
                if (view.isBackButton(click.x, click.y)) {
                    return;
                }
            }
        } finally {
            handler.removeObject(view);
        }
    }

    /** Half-open rect hit-test, same convention as RulesView.isBackButton. */
    public boolean isBackButton(int px, int py) {
        return px >= BACK_LEFT && px < BACK_RIGHT && py >= BACK_TOP && py < BACK_BOTTOM;
    }

    /** Half-open rect hit-test for the ON/OFF toggle. */
    public boolean isToggleHotspot(int px, int py) {
        return px >= TOGGLE_LEFT && px < TOGGLE_RIGHT && py >= TOGGLE_TOP && py < TOGGLE_BOTTOM;
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
        g.drawRect(TOGGLE_LEFT, TOGGLE_TOP, TOGGLE_RIGHT - TOGGLE_LEFT - 1, TOGGLE_BOTTOM - TOGGLE_TOP - 1);
        String state = gameSettings.totalBetsCannotEqualTricks ? "ON" : "OFF";
        drawCenteredIn(g, state, TOGGLE_LEFT, TOGGLE_RIGHT, TOGGLE_BOTTOM - 10);

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
