import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ROADMAP item 2: the Achievements list modal, reachable from the Start
 * Screen's new Achievements button. Same lifecycle shape as RulesView
 * (full 840x630 canvas GameObject, added to the Handler, blocking click loop
 * awaiting the Back button, removed in a finally via showBlocking) and reuses
 * its exact panel/Back-button geometry for visual consistency -- see
 * RulesView's class doc for why this doesn't extend ModalOverlay (that
 * class's panel is fixed at 640x430, too small for this page's content).
 *
 * Lists every non-hidden Achievement (name + description always visible,
 * whether locked or unlocked, plus the unlock date if unlocked). Hidden
 * achievements (just BAPI_EASTER_EGG today) are omitted entirely unless
 * already unlocked, per the design spec.
 */
public class AchievementsView extends GameObject {
    private static final int PANEL_X = 40, PANEL_Y = 20, PANEL_W = 760, PANEL_H = 590;
    private static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    private static final int TITLE_Y = 60;
    private static final int LIST_TOP = 95;
    private static final int ROW_HEIGHT = 38;
    private static final int NAME_OFFSET = 14;
    private static final int STATUS_OFFSET = 32;

    private static final int BACK_TOP = 576, BACK_BOTTOM = 602;
    private static final int BACK_LEFT = 680, BACK_RIGHT = 760;

    private static final DateTimeFormatter UNLOCK_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /** One renderable row: the static definition plus this save's unlock state for it. */
    public static class Row {
        public final Achievement achievement;
        public final boolean unlocked;
        public final Instant unlockedAt;

        Row(Achievement achievement, boolean unlocked, Instant unlockedAt) {
            this.achievement = achievement;
            this.unlocked = unlocked;
            this.unlockedAt = unlockedAt;
        }
    }

    private final SaveData saveData;

    public AchievementsView(SaveData saveData) {
        this.saveData = saveData;
    }

    /**
     * Same lifecycle shape as RulesView.showBlocking: add to the Handler,
     * block on clicks until the Back button is hit, remove in a finally --
     * including the same ACHIEVEMENTTOAST click-to-dismiss check ahead of
     * the Back button, for the same code-review Finding 1 reason (see
     * RulesView.showBlocking's doc).
     */
    public static void showBlocking(Handler handler, MouseInput mouseInput, SaveData saveData, AchievementToast achievementToast) {
        AchievementsView view = new AchievementsView(saveData);
        handler.addObject(view);
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    achievementToast.dismiss();
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

    /** Half-open rect hit-test, same convention/geometry as RulesView.isBackButton. */
    public boolean isBackButton(int px, int py) {
        return px >= BACK_LEFT && px < BACK_RIGHT && py >= BACK_TOP && py < BACK_BOTTOM;
    }

    /**
     * Every non-hidden achievement, plus any hidden one already unlocked, in
     * Achievement.values() declaration order -- pulled out from render() so
     * the hidden-omission/unlock-wiring logic is unit-testable without a
     * Graphics context.
     */
    public List<Row> visibleRows() {
        List<Row> rows = new ArrayList<>();
        for (Achievement achievement : Achievement.values()) {
            boolean unlocked = saveData.isUnlocked(achievement);
            if (achievement.isHidden() && !unlocked) {
                continue;
            }
            rows.add(new Row(achievement, unlocked, saveData.unlockedAt(achievement)));
        }
        return rows;
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
        drawCentered(g, "Achievements", TITLE_Y);

        int rowTop = LIST_TOP;
        for (Row row : visibleRows()) {
            g.setFont(ModalOverlay.HEADER_FONT);
            g.setColor(row.unlocked ? ModalOverlay.GOLD : Color.BLACK);
            g.drawString(row.achievement.getDisplayName(), CONTENT_LEFT, rowTop + NAME_OFFSET);

            g.setFont(defaultFont);
            g.setColor(Color.DARK_GRAY);
            String status = row.unlocked
                    ? row.achievement.getDescription() + "  -- Unlocked " + UNLOCK_DATE_FORMAT.format(row.unlockedAt)
                    : row.achievement.getDescription() + "  -- Locked";
            g.drawString(status, CONTENT_LEFT, rowTop + STATUS_OFFSET);

            rowTop += ROW_HEIGHT;
        }

        g.setFont(defaultFont);
        g.setColor(Color.BLACK);
        g.drawRect(BACK_LEFT, BACK_TOP, BACK_RIGHT - BACK_LEFT - 1, BACK_BOTTOM - BACK_TOP - 1);
        drawCenteredIn(g, "Back", BACK_LEFT, BACK_RIGHT, BACK_BOTTOM - 8);
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
