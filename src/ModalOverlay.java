import java.awt.*;

/**
 * Shared full-canvas scrim + centered panel geometry for (a) RoundSummaryPanel
 * and (c) GameOverBanner (ROADMAP item 1). Both are added to the Handler
 * last, so the Handler/CopyOnWriteArrayList's insertion-order render contract
 * (see Handler's class doc) puts them on top of literally everything already
 * on screen that frame -- a full-canvas modal robust by construction against
 * every occupied region in the game, rather than fragile gap-fitting
 * arithmetic (which is what produced the trick-indicators' pixel-collision
 * bugs -- see DONE.md).
 *
 * (b) NextTrickPrompt does NOT use this: it must not hide the board (the
 * player needs to see the just-resolved trick), so it's a small standalone
 * GameObject placed in a verified-clear gap instead.
 */
public abstract class ModalOverlay extends GameObject {
    /**
     * Same RGB as Card.HIGH_CARD_COLOR (private there) -- a deliberate
     * callback to the existing "gold = you got it exactly right" visual
     * language established by the high-card ring. Duplicated as a literal
     * rather than widening Card's visibility for a single reuse; see the
     * completion report for this call.
     */
    protected static final Color GOLD = new Color(204, 153, 0);

    protected static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 22);
    protected static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 13);

    private static final int PANEL_X = 100, PANEL_Y = 100, PANEL_W = 640, PANEL_H = 430;
    /** Content margin is 30px in from the panel's edges. */
    protected static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    @Override
    public void tick() {
        //static content -- nothing to update per frame
    }

    @Override
    public void render(Graphics g) {
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);

        g.setColor(Color.WHITE);
        g.fillRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        g.setColor(Color.BLACK);
        g.drawRect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        renderContent(g);
    }

    /** Subclass hook: draw everything inside the panel (title, table, etc). */
    protected abstract void renderContent(Graphics g);

    /**
     * Draws TEXT horizontally centered within the panel's content span
     * (CONTENT_LEFT..CONTENT_RIGHT), using G's currently-set font -- caller
     * must set the desired font before calling this.
     */
    protected static void drawCentered(Graphics g, String text, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int x = CONTENT_LEFT + ((CONTENT_RIGHT - CONTENT_LEFT) - width) / 2;
        g.drawString(text, x, y);
    }
}
