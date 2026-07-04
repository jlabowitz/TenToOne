import java.awt.*;

/**
 * ROADMAP item 1: the pre-launch splash screen -- title, subtitle, name-entry
 * field, and Rules/Start Game buttons. A full 840x630 canvas GameObject, same
 * lifecycle shape as BetStepper/NextTrickPrompt (added to the Handler, the
 * blocking game-logic thread loops on mouseInput.awaitClick(), removed via
 * handler.removeObject() in a finally once done) -- but that blocking loop is
 * Game.runStartScreen()'s job (backend pass), not this class's.
 *
 * Does NOT extend ModalOverlay: that class's panel geometry is fixed at
 * 640x430 for a dialog layered over existing board content, whereas this is
 * a full splash page shown before any board exists. Paints its own opaque
 * white background as the first step of render() (same defensive reasoning
 * as RulesView's own background fill) rather than relying on Game.render()'s
 * per-frame white fill underneath: that fill is only a blank canvas the
 * first time this screen shows, before any Player exists. On a Play Again
 * restart (Game.restartForNewGame()), this screen reappears while the same
 * Player objects remain registered in the Handler for the whole session
 * (deliberately never re-added/removed -- see restartForNewGame()'s class
 * doc), so without its own fill, AI players' HUD text would render
 * underneath and bleed through around this screen's own content.
 *
 * Implements TypingTarget so KeyInput.setTarget(this) can route keystrokes
 * from the name field directly into the (volatile) name buffer -- see
 * KeyInput's class doc for why a plain volatile-String reassignment is
 * correct here instead of a queue (mirrors Player.trickLeader/leadingSuit's
 * write-one-thread/read-another-thread convention).
 */
public class StartScreen extends GameObject implements TypingTarget {
    public enum Control { RULES, START }

    /**
     * Verified via actual FontMetrics measurement, not the design spec's
     * rough ~7px/glyph estimate of 16 -- see this item's completion report
     * for the measured numbers. The binding constraint is
     * RoundSummaryPanel's NAME_X..BET_X column (130..310, 180px wide), which
     * renders "<name> (you)" for the human in that column's default font
     * (Dialog, plain, 12 -- confirmed via BufferedImage.createGraphics()).
     * The widest glyph in that font among letters/digits is 'W' (11px);
     * " (you)" costs 30px. A name of 13 repeated 'W's + " (you)" measures
     * 173px (fits, 7px clearance); 14 repeated 'W's measures 184px (would
     * overflow into the Bet column). 13 is therefore the largest value that
     * can't overflow that column even in the adversarial worst case.
     */
    public static final int MAX_NAME_LENGTH = 13;

    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 36);

    private static final int TITLE_Y = 120;
    private static final int SUBTITLE_Y = 165;

    private static final int NAME_LABEL_X = 270;
    private static final int NAME_LABEL_Y = 245;

    private static final int FIELD_TOP = 260;
    private static final int FIELD_BOTTOM = 294;
    private static final int FIELD_LEFT = 270;
    private static final int FIELD_RIGHT = 570;

    private static final int FIELD_TEXT_X = FIELD_LEFT + 10;
    private static final int FIELD_TEXT_Y = FIELD_BOTTOM - 10;

    private static final int RULES_TOP = 320, RULES_BOTTOM = 354;
    private static final int RULES_LEFT = 270, RULES_RIGHT = 390;

    private static final int START_TOP = 320, START_BOTTOM = 354;
    private static final int START_LEFT = 450, START_RIGHT = 570;

    private static final int FOOTER_Y = 560;

    /** ~0.5s at 60 ticks/sec -- matches IllegalPlayFeedback/BetStepper's tick-rate assumption. */
    private static final int CURSOR_BLINK_INTERVAL_TICKS = 30;

    private volatile String name = "";
    private int frameCounter = 0;
    private boolean cursorVisible = true;

    /**
     * Returns the control at pixel (px, py), or null if the point hits no
     * control (the name field itself, a gap, or outside both buttons
     * entirely). Half-open rects, same convention as BetStepper.controlAt.
     */
    public Control controlAt(int px, int py) {
        if (px >= RULES_LEFT && px < RULES_RIGHT && py >= RULES_TOP && py < RULES_BOTTOM) {
            return Control.RULES;
        }
        if (px >= START_LEFT && px < START_RIGHT && py >= START_TOP && py < START_BOTTOM) {
            return Control.START;
        }
        return null;
    }

    @Override
    public void typeChar(char c) {
        if (name.length() < MAX_NAME_LENGTH) {
            name = name + c;
        }
    }

    @Override
    public void backspace() {
        if (!name.isEmpty()) {
            name = name.substring(0, name.length() - 1);
        }
    }

    /**
     * Returns the raw typed buffer. The caller (Game's constructor, backend
     * pass) is responsible for .trim() and the empty-check at submit time --
     * not this class's job.
     */
    public String getName() {
        return name;
    }

    @Override
    public void tick() {
        frameCounter++;
        if (frameCounter % CURSOR_BLINK_INTERVAL_TICKS == 0) {
            cursorVisible = !cursorVisible;
        }
    }

    @Override
    public void render(Graphics g) {
        Font defaultFont = g.getFont();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);

        g.setFont(TITLE_FONT);
        g.setColor(Color.BLACK);
        drawCentered(g, "Ten to One", TITLE_Y);

        g.setFont(defaultFont);
        drawCentered(g, "A trick-taking card game for up to 5 players", SUBTITLE_Y);

        g.setFont(defaultFont.deriveFont(Font.BOLD));
        g.drawString("Your name:", NAME_LABEL_X, NAME_LABEL_Y);
        g.setFont(defaultFont);

        g.setColor(Color.BLACK);
        g.drawRect(FIELD_LEFT, FIELD_TOP, FIELD_RIGHT - FIELD_LEFT - 1, FIELD_BOTTOM - FIELD_TOP - 1);

        String typedText = name;
        if (typedText.isEmpty()) {
            g.setFont(defaultFont.deriveFont(Font.ITALIC));
            g.setColor(Color.GRAY);
            g.drawString("e.g. Alex", FIELD_TEXT_X, FIELD_TEXT_Y);
            g.setFont(defaultFont);
        } else {
            g.setColor(Color.BLACK);
            g.drawString(typedText, FIELD_TEXT_X, FIELD_TEXT_Y);
        }

        if (cursorVisible) {
            FontMetrics metrics = g.getFontMetrics();
            int cursorX = FIELD_TEXT_X + metrics.stringWidth(typedText);
            g.setColor(Color.BLACK);
            g.drawLine(cursorX, FIELD_TOP + 6, cursorX, FIELD_BOTTOM - 6);
        }

        g.setColor(Color.BLACK);
        g.drawRect(RULES_LEFT, RULES_TOP, RULES_RIGHT - RULES_LEFT - 1, RULES_BOTTOM - RULES_TOP - 1);
        drawCenteredIn(g, "Rules", RULES_LEFT, RULES_RIGHT, RULES_BOTTOM - 10);

        g.drawRect(START_LEFT, START_TOP, START_RIGHT - START_LEFT - 1, START_BOTTOM - START_TOP - 1);
        drawCenteredIn(g, "Start Game", START_LEFT, START_RIGHT, START_BOTTOM - 10);

        g.setColor(Color.BLACK);
        // "16" in the design spec's illustrative copy was written against an
        // unverified estimate of MAX_NAME_LENGTH; corrected here to match
        // the measured value above so the footer doesn't lie to the player.
        drawCentered(g, "Up to " + MAX_NAME_LENGTH + " characters. Click Start Game to begin.", FOOTER_Y);
    }

    /** Draws TEXT horizontally centered on the full 840-wide canvas. */
    private static void drawCentered(Graphics g, String text, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int x = (Game.WIDTH - width) / 2;
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
