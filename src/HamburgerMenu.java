import java.awt.*;

/**
 * ROADMAP item 10: the hamburger-menu dropdown, opened via the hamburger icon
 * hotspot each of BetStepper/IllegalPlayFeedback/NextTrickPrompt independently
 * renders+hit-tests (see those classes' HAMBURGER_* fields). A small panel
 * near the icon -- like NextTrickPrompt, deliberately NOT a full-canvas modal
 * (does not extend ModalOverlay) -- it must not hide the board while open.
 * Reuses ModalOverlay.GOLD/TITLE_FONT/HEADER_FONT for visual consistency with
 * the rest of this codebase's overlay chrome.
 *
 * Lists the same 6 items as before -- Pause, Rules, Settings, Achievements,
 * Restart Game, Go to Menu -- but as of this user-feedback pass, laid out as
 * a single vertical column (1 col x 6 rows) rather than the earlier 3x2 grid.
 * The earlier 3x2 layout existed specifically to keep the dropdown clear of
 * the AI seat row's name/card/score text, the y=280 message band, and the
 * trump card (see TestHamburgerIconGeometry's now-superseded algebraic
 * proof) -- the user has since explicitly asked for a single column instead,
 * overriding that constraint and accepting that the open panel now overlaps
 * those regions while it's showing. Only the *closed* hamburger icon
 * (HAMBURGER_LEFT/RIGHT/TOP/BOTTOM in BetStepper/IllegalPlayFeedback/
 * NextTrickPrompt, unchanged by this pass) still needs to clear the AI seat
 * row -- that geometry is untouched here.
 *
 * Row geometry (ROW_WIDTH=130, ROW_HEIGHT=30) is derived from the legacy
 * Achievements button's own clickable size (130w x 30h, StartScreen's
 * pre-this-pass ACHIEVEMENTS_* fields) per the user's explicit ask to reuse
 * that reference size for these larger, single-column rows.
 *
 * Clicking outside the panel dismisses with no action, matching this
 * codebase's standard miss-click convention (RulesView/AchievementsView's
 * showBlocking loops ignore misses the same way).
 *
 * Restart requires a second, explicit confirmation click (ROADMAP item 10's
 * own spec: "don't restart on the first click") -- selecting Restart swaps
 * to a separate, small, centered confirmation panel ("Are you sure?" [Yes]
 * [No]) rather than reusing the main dropdown's own (top-left-anchored,
 * differently-sized) bounds. Only Yes resolves showBlocking() with
 * Selection.RESTART; No dismisses the whole menu with no action at all
 * (rather than returning to the 6-item list) -- the simplest of a few
 * reasonable choices here, called out in this item's completion report as a
 * judgment call, not a spec'd requirement.
 */
public class HamburgerMenu extends GameObject {
    public enum Selection { RULES, ACHIEVEMENTS, SETTINGS, MENU, RESTART, PAUSE }

    /**
     * Fix (parallel-array cleanup): a single ordered list of (selection,
     * label) pairs, replacing the old two-parallel-arrays-by-index design --
     * reordering the menu is now just reordering entries here, with no way
     * for a selection/label pair to desync.
     */
    private record MenuItem(Selection selection, String label) {
    }

    private static final MenuItem[] MENU_ITEMS = {
            new MenuItem(Selection.PAUSE, "Pause"),
            new MenuItem(Selection.RULES, "Rules"),
            new MenuItem(Selection.SETTINGS, "Settings"),
            new MenuItem(Selection.ACHIEVEMENTS, "Achievements"),
            new MenuItem(Selection.RESTART, "Restart Game"),
            new MenuItem(Selection.MENU, "Go to Menu"),
    };

    /**
     * Panel bounds. PANEL_LEFT/PANEL_TOP match the hamburger icon's own
     * HAMBURGER_LEFT/HAMBURGER_TOP (BetStepper/IllegalPlayFeedback/
     * NextTrickPrompt) -- opening the menu visually replaces the closed
     * 3-line icon with this panel, anchored at the same corner.
     *
     * GRID_LEFT/ROW_WIDTH/PANEL_RIGHT and ROW_TOP/ROW_HEIGHT/ROWS/
     * PANEL_BOTTOM are plain single-column layout math (10px left margin,
     * 6px top margin, 130x30 rows, 6px bottom margin) -- no cross-element
     * clearance proof is needed for this layout anymore (see class doc).
     */
    private static final int PANEL_LEFT = 10;
    private static final int GRID_LEFT = PANEL_LEFT + 10;
    private static final int ROW_WIDTH = 130;
    private static final int PANEL_RIGHT = GRID_LEFT + ROW_WIDTH + 10;

    private static final int PANEL_TOP = 5;
    private static final int ROW_TOP = PANEL_TOP + 6;
    private static final int ROW_HEIGHT = 30;
    private static final int ROWS = 6;
    private static final int PANEL_BOTTOM = ROW_TOP + ROWS * ROW_HEIGHT + 6;

    // Restart confirmation sub-view -- its own small panel, centered on the
    // 840x630 canvas (Game.WIDTH/HEIGHT), rather than reusing the main
    // dropdown's own top-left-anchored/differently-shaped bounds. Smaller
    // than PauseView's own centered panel (300x160) since this only needs
    // room for one line of text plus a Yes/No row.
    private static final int CONFIRM_PANEL_W = 260, CONFIRM_PANEL_H = 120;
    private static final int CONFIRM_PANEL_X = (Game.WIDTH - CONFIRM_PANEL_W) / 2;
    private static final int CONFIRM_PANEL_Y = (Game.HEIGHT - CONFIRM_PANEL_H) / 2;

    private static final int CONFIRM_TEXT_X = CONFIRM_PANEL_X + 20, CONFIRM_TEXT_Y = CONFIRM_PANEL_Y + 45;
    private static final int CONFIRM_YES_LEFT = 330, CONFIRM_YES_RIGHT = 410;
    private static final int CONFIRM_NO_LEFT = 430, CONFIRM_NO_RIGHT = 510;
    private static final int CONFIRM_ROW_TOP = 325, CONFIRM_ROW_BOTTOM = 357;

    private boolean confirmingRestart = false;

    /**
     * Same lifecycle shape as RulesView/AchievementsView.showBlocking: add to
     * the Handler, block on clicks until a real selection resolves (or a
     * miss-click outside the panel dismisses with null), remove in a finally.
     * ACHIEVEMENTTOAST's click-to-dismiss hotspot is checked first, same
     * code-review Finding 1 reason every other showBlocking loop in this
     * codebase already follows.
     */
    public static Selection showBlocking(Handler handler, MouseInput mouseInput, AchievementToast achievementToast) {
        HamburgerMenu menu = new HamburgerMenu();
        handler.addObject(menu);
        InteractionLog.logShown("HamburgerMenu");
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (menu.confirmingRestart) {
                    if (menu.isConfirmYesHotspot(click.x, click.y)) {
                        InteractionLog.logClick(click.x, click.y, "HamburgerMenu confirm-restart Yes");
                        return Selection.RESTART;
                    }
                    if (menu.isConfirmNoHotspot(click.x, click.y)) {
                        InteractionLog.logClick(click.x, click.y, "HamburgerMenu confirm-restart No");
                        return null;
                    }
                    if (!menu.isInsidePanel(click.x, click.y)) {
                        InteractionLog.logClick(click.x, click.y, "outside-panel dismiss (HamburgerMenu confirm-restart)");
                        return null;
                    }
                    InteractionLog.logClick(click.x, click.y, "no control matched (HamburgerMenu confirm-restart)");
                    continue;
                }
                Selection item = menu.itemAt(click.x, click.y);
                if (item == Selection.RESTART) {
                    InteractionLog.logClick(click.x, click.y, "HamburgerMenu.RESTART");
                    menu.confirmingRestart = true;
                    InteractionLog.logShown("HamburgerMenu confirm-restart");
                    continue;
                }
                if (item != null) {
                    InteractionLog.logClick(click.x, click.y, "HamburgerMenu." + item);
                    return item;
                }
                if (!menu.isInsidePanel(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "outside-panel dismiss (HamburgerMenu)");
                    return null;
                }
                InteractionLog.logClick(click.x, click.y, "no control matched (HamburgerMenu)");
            }
        } finally {
            handler.removeObject(menu);
        }
    }

    /**
     * Half-open rect hit-test for whichever panel is currently showing --
     * the main dropdown's bounds normally, or the confirm sub-view's own
     * (differently positioned/sized) bounds once Restart has been selected.
     * showBlocking's loop already calls this from both branches expecting
     * exactly this state-aware behavior.
     */
    public boolean isInsidePanel(int px, int py) {
        if (confirmingRestart) {
            return px >= CONFIRM_PANEL_X && px < CONFIRM_PANEL_X + CONFIRM_PANEL_W
                    && py >= CONFIRM_PANEL_Y && py < CONFIRM_PANEL_Y + CONFIRM_PANEL_H;
        }
        return px >= PANEL_LEFT && px < PANEL_RIGHT && py >= PANEL_TOP && py < PANEL_BOTTOM;
    }

    /**
     * Returns the item at (px, py), or null if the click hits no row (still
     * possibly inside the panel -- see isInsidePanel). Single column now:
     * row = (py - ROW_TOP) / ROW_HEIGHT, valid iff px falls within the row's
     * width and row falls within [0, ROWS).
     */
    public Selection itemAt(int px, int py) {
        if (px < GRID_LEFT || px >= GRID_LEFT + ROW_WIDTH || py < ROW_TOP) {
            return null;
        }
        int row = (py - ROW_TOP) / ROW_HEIGHT;
        if (row < 0 || row >= ROWS) {
            return null;
        }
        return MENU_ITEMS[row].selection();
    }

    public boolean isConfirmYesHotspot(int px, int py) {
        return px >= CONFIRM_YES_LEFT && px < CONFIRM_YES_RIGHT && py >= CONFIRM_ROW_TOP && py < CONFIRM_ROW_BOTTOM;
    }

    public boolean isConfirmNoHotspot(int px, int py) {
        return px >= CONFIRM_NO_LEFT && px < CONFIRM_NO_RIGHT && py >= CONFIRM_ROW_TOP && py < CONFIRM_ROW_BOTTOM;
    }

    @Override
    public void tick() {
        //static content -- nothing to update per frame
    }

    @Override
    public void render(Graphics g) {
        if (confirmingRestart) {
            renderConfirm(g);
            return;
        }

        g.setColor(Color.WHITE);
        g.fillRect(PANEL_LEFT, PANEL_TOP, PANEL_RIGHT - PANEL_LEFT, PANEL_BOTTOM - PANEL_TOP);
        g.setColor(Color.BLACK);
        g.drawRect(PANEL_LEFT, PANEL_TOP, PANEL_RIGHT - PANEL_LEFT - 1, PANEL_BOTTOM - PANEL_TOP - 1);

        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int cellTop = ROW_TOP + i * ROW_HEIGHT;
            int baselineY = verticalCenterBaseline(metrics, cellTop, ROW_HEIGHT);
            g.drawString(MENU_ITEMS[i].label(), GRID_LEFT, baselineY);
        }
    }

    /**
     * Baseline y that vertically centers a line of text (per the given
     * FontMetrics) within a cellTop..cellTop+cellHeight band -- standard
     * ascent/descent centering math, used instead of a magic fixed offset.
     */
    private static int verticalCenterBaseline(FontMetrics metrics, int cellTop, int cellHeight) {
        return cellTop + (cellHeight + metrics.getAscent() - metrics.getDescent()) / 2;
    }

    /**
     * Draws the confirm sub-view's own panel (box, "Are you sure?" text, and
     * a Yes/No row) -- this is a distinct, centered panel from the main
     * dropdown's own top-left-anchored bounds, not a reuse of it.
     */
    private void renderConfirm(Graphics g) {
        g.setColor(Color.WHITE);
        g.fillRect(CONFIRM_PANEL_X, CONFIRM_PANEL_Y, CONFIRM_PANEL_W, CONFIRM_PANEL_H);
        g.setColor(Color.BLACK);
        g.drawRect(CONFIRM_PANEL_X, CONFIRM_PANEL_Y, CONFIRM_PANEL_W - 1, CONFIRM_PANEL_H - 1);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.setColor(Color.BLACK);
        g.drawString("Are you sure?", CONFIRM_TEXT_X, CONFIRM_TEXT_Y);

        g.drawRect(CONFIRM_YES_LEFT, CONFIRM_ROW_TOP, CONFIRM_YES_RIGHT - CONFIRM_YES_LEFT - 1, CONFIRM_ROW_BOTTOM - CONFIRM_ROW_TOP - 1);
        drawCenteredIn(g, "Yes", CONFIRM_YES_LEFT, CONFIRM_YES_RIGHT, CONFIRM_ROW_BOTTOM - 8);

        g.drawRect(CONFIRM_NO_LEFT, CONFIRM_ROW_TOP, CONFIRM_NO_RIGHT - CONFIRM_NO_LEFT - 1, CONFIRM_ROW_BOTTOM - CONFIRM_ROW_TOP - 1);
        drawCenteredIn(g, "No", CONFIRM_NO_LEFT, CONFIRM_NO_RIGHT, CONFIRM_ROW_BOTTOM - 8);
    }

    /** Draws TEXT horizontally centered within [left, right). */
    private static void drawCenteredIn(Graphics g, String text, int left, int right, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int x = left + ((right - left) - width) / 2;
        g.drawString(text, x, y);
    }
}
