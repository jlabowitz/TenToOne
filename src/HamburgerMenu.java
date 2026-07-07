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
 * Lists the same 6 items as before -- Rules, Achievements, Settings, Menu,
 * Restart, Pause -- but as of the user-feedback pass that moved the
 * hamburger icon to the very top of the canvas (HAMBURGER_TOP/BOTTOM in
 * BetStepper/IllegalPlayFeedback/NextTrickPrompt), this is a 3-column x
 * 2-row grid (reading order preserved: Rules, Achievements, Settings on row
 * 0; Menu, Restart, Pause on row 1) rather than a single vertical column.
 * This is a forced consequence of real, measured geometry, not a cosmetic
 * preference -- see PANEL_TOP/PANEL_BOTTOM's own doc for the full clearance
 * arithmetic (TestHamburgerIconGeometry has the algebraic proof): a
 * 6-row-tall single column anchored near y=0 cannot fit above where the
 * AI seat row's own name/card/score text needs to sit without either (a)
 * pushing that row down far enough to collide with the fixed y=280 message
 * band NextTrickPrompt/IllegalPlayFeedback already occupy (itself wedged
 * between the AI row and the trump card's y=305 top edge, with little slack
 * to spare), or (b) colliding with the trump card directly. Going wide
 * instead of tall sidesteps both: this panel's width doesn't compete with
 * anything else near the top of the canvas, only its height does.
 *
 * Clicking outside the panel dismisses with no action, matching this
 * codebase's standard miss-click convention (RulesView/AchievementsView's
 * showBlocking loops ignore misses the same way).
 *
 * Restart requires a second, explicit confirmation click (ROADMAP item 10's
 * own spec: "don't restart on the first click") -- selecting Restart swaps
 * this same panel's contents to "Are you sure? [Yes] [No]" instead of acting
 * immediately. Only Yes resolves showBlocking() with Selection.RESTART; No
 * dismisses the whole menu with no action at all (rather than returning to
 * the 6-item list) -- the simplest of a few reasonable choices here, called
 * out in this item's completion report as a judgment call, not a spec'd
 * requirement.
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
            new MenuItem(Selection.RULES, "Rules"),
            new MenuItem(Selection.ACHIEVEMENTS, "Achievements"),
            new MenuItem(Selection.SETTINGS, "Settings"),
            new MenuItem(Selection.MENU, "Menu"),
            new MenuItem(Selection.RESTART, "Restart"),
            new MenuItem(Selection.PAUSE, "Pause"),
    };

    /**
     * Panel bounds. PANEL_TOP matches the hamburger icon's own HAMBURGER_TOP
     * (BetStepper/IllegalPlayFeedback/NextTrickPrompt) -- opening the menu
     * visually replaces the closed 3-line icon with this panel, same
     * convention the original design used (PANEL_TOP was HAMBURGER_TOP there
     * too).
     *
     * PANEL_BOTTOM=50 is the actual binding number in this whole layout,
     * derived from (not eyeballed): Game.AI_ROW_Y=70 (the AI seat row's
     * shared render y) minus the default font's ascent (13, measured via
     * headless FontMetrics on a 1x1 BufferedImage, same technique
     * TestPlayer/TestHamburgerIconGeometry already use) minus a small
     * (~7px) clearance margin -- i.e. this panel's bottom edge must sit
     * above every AI seat's name-text top edge, for every seat, at every
     * supported player count. Because every AI seat shares the same render
     * y (Game.renderPlayers()), this is one inequality regardless of how
     * many AI opponents are seated or how far right they sit -- unlike the
     * old single-column design, this panel's *width* is unconstrained by
     * any of this (see PANEL_RIGHT), only its height is.
     *
     * PANEL_RIGHT=330 is wide enough for 3 columns of ~100px (the widest
     * label, "Achievements", measures 77px in the default font -- see
     * TestHamburgerIconGeometry) with room to spare, and was never
     * constrained by anything else on screen: nothing else currently
     * occupies this row's full-width horizontal band once the vertical
     * clearance above holds (AchievementToast's own dismiss band is the one
     * exception -- see the HAMBURGER_TOP fields' own doc for that
     * documented, accepted trade-off).
     */
    private static final int PANEL_LEFT = 10, PANEL_RIGHT = 330;
    private static final int PANEL_TOP = 5, PANEL_BOTTOM = 50;

    private static final int GRID_LEFT = PANEL_LEFT + 10;
    private static final int COL_WIDTH = 100;
    private static final int COLS = 3;

    private static final int ROW_TOP = PANEL_TOP + 6;
    private static final int ROW_HEIGHT = 18;
    private static final int ROWS = 2;

    // Confirm ("Are you sure?") sub-view -- laid out horizontally (text,
    // then Yes/No side by side) rather than the original's vertical stack,
    // since this panel is now wide-and-short rather than narrow-and-tall.
    private static final int CONFIRM_TEXT_X = GRID_LEFT, CONFIRM_TEXT_Y = 27;
    private static final int CONFIRM_YES_LEFT = 130, CONFIRM_YES_RIGHT = 190;
    private static final int CONFIRM_NO_LEFT = 210, CONFIRM_NO_RIGHT = 270;
    private static final int CONFIRM_ROW_TOP = 13, CONFIRM_ROW_BOTTOM = 43;

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
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    achievementToast.dismiss();
                    continue;
                }
                if (menu.confirmingRestart) {
                    if (menu.isConfirmYesHotspot(click.x, click.y)) {
                        return Selection.RESTART;
                    }
                    if (menu.isConfirmNoHotspot(click.x, click.y)) {
                        return null;
                    }
                    if (!menu.isInsidePanel(click.x, click.y)) {
                        return null;
                    }
                    continue;
                }
                Selection item = menu.itemAt(click.x, click.y);
                if (item == Selection.RESTART) {
                    menu.confirmingRestart = true;
                    continue;
                }
                if (item != null) {
                    return item;
                }
                if (!menu.isInsidePanel(click.x, click.y)) {
                    return null;
                }
            }
        } finally {
            handler.removeObject(menu);
        }
    }

    /** Half-open rect hit-test for the panel itself, used to distinguish an inert click on the panel from a true miss-click. */
    public boolean isInsidePanel(int px, int py) {
        return px >= PANEL_LEFT && px < PANEL_RIGHT && py >= PANEL_TOP && py < PANEL_BOTTOM;
    }

    /** Returns the item at (px, py), or null if the click hits no grid cell (still possibly inside the panel -- see isInsidePanel). */
    public Selection itemAt(int px, int py) {
        if (px < GRID_LEFT || py < ROW_TOP) {
            return null;
        }
        int col = (px - GRID_LEFT) / COL_WIDTH;
        int row = (py - ROW_TOP) / ROW_HEIGHT;
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return null;
        }
        int index = row * COLS + col;
        if (index < 0 || index >= MENU_ITEMS.length) {
            return null;
        }
        return MENU_ITEMS[index].selection();
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
        g.setColor(Color.WHITE);
        g.fillRect(PANEL_LEFT, PANEL_TOP, PANEL_RIGHT - PANEL_LEFT, PANEL_BOTTOM - PANEL_TOP);
        g.setColor(Color.BLACK);
        g.drawRect(PANEL_LEFT, PANEL_TOP, PANEL_RIGHT - PANEL_LEFT - 1, PANEL_BOTTOM - PANEL_TOP - 1);

        if (confirmingRestart) {
            renderConfirm(g);
            return;
        }

        g.setColor(Color.BLACK);
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int row = i / COLS;
            int col = i % COLS;
            int cellLeft = GRID_LEFT + col * COL_WIDTH;
            int cellTop = ROW_TOP + row * ROW_HEIGHT;
            int baselineY = verticalCenterBaseline(metrics, cellTop, ROW_HEIGHT);
            g.drawString(MENU_ITEMS[i].label(), cellLeft, baselineY);
        }
    }

    /**
     * Baseline y that vertically centers a line of text (per the given
     * FontMetrics) within a cellTop..cellTop+cellHeight band -- standard
     * ascent/descent centering math, used instead of a magic fixed offset
     * (like the original single-column design's "rowTop + ROW_HEIGHT - 10")
     * since ROW_HEIGHT here (18px) is tight enough that a fixed offset tuned
     * for the old 32px rows would clip text.
     */
    private static int verticalCenterBaseline(FontMetrics metrics, int cellTop, int cellHeight) {
        return cellTop + (cellHeight + metrics.getAscent() - metrics.getDescent()) / 2;
    }

    /**
     * Text on the left, Yes/No boxes to its right -- horizontal, not
     * vertical like the original design, since this panel is now wide (330px)
     * and short (45px) rather than narrow and tall. Font is left as
     * ModalOverlay.HEADER_FONT for all three drawStrings below (never reset
     * back to the default in between), matching the original design's own
     * (likely unintentional, but harmless and unchanged here) behavior of
     * rendering "Yes"/"No" in the header font too.
     */
    private void renderConfirm(Graphics g) {
        g.setFont(ModalOverlay.HEADER_FONT);
        g.setColor(Color.BLACK);
        g.drawString("Are you sure?", CONFIRM_TEXT_X, CONFIRM_TEXT_Y);

        g.drawRect(CONFIRM_YES_LEFT, CONFIRM_ROW_TOP, CONFIRM_YES_RIGHT - CONFIRM_YES_LEFT - 1, CONFIRM_ROW_BOTTOM - CONFIRM_ROW_TOP - 1);
        g.drawString("Yes", CONFIRM_YES_LEFT + 20, CONFIRM_ROW_BOTTOM - 8);

        g.drawRect(CONFIRM_NO_LEFT, CONFIRM_ROW_TOP, CONFIRM_NO_RIGHT - CONFIRM_NO_LEFT - 1, CONFIRM_ROW_BOTTOM - CONFIRM_ROW_TOP - 1);
        g.drawString("No", CONFIRM_NO_LEFT + 25, CONFIRM_ROW_BOTTOM - 8);
    }
}
