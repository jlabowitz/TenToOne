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
 *
 * ROADMAP item 10 follow-up: three-row layout, added by this pass (added the
 * Settings button and reshuffled Resume/Rules/Achievements around it):
 *
 * Row 1 (y=320-354): "Resume Game" + "Start Game" side by side when a
 * resumable saved game exists; otherwise "Start Game" alone, centered in the
 * gap between them -- Rules does NOT move up into this row either way.
 *
 * Row 2 (y=370-404): "Rules" (directly under Row 1's old Rules slot) +
 * "Achievements" to its right -- always shown, regardless of hasResumableGame.
 *
 * Row 3 (y=460-494): "Settings", reusing the exact slot Resume used to occupy
 * alone (its clearance against the stats line/footer below is already proven
 * by this class's own resumeButtonClearsStatsLineAndFooterText-style test).
 */
public class StartScreen extends GameObject implements TypingTarget {
    public enum Control { RULES, START, ACHIEVEMENTS, RESUME, SETTINGS }

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

    // --- Row 1 (y=320-354): Resume Game + Start Game, or Start Game alone ---
    private static final int ROW1_TOP = 320, ROW1_BOTTOM = 354;

    /** Resume's slot -- only rendered/clickable when hasResumableGame is true. */
    private static final int RESUME_LEFT = 270, RESUME_RIGHT = 390;

    /** Start's slot when Resume is also shown -- unchanged from before this pass. */
    private static final int START_WITH_RESUME_LEFT = 450, START_WITH_RESUME_RIGHT = 570;

    /** Start's slot when shown alone -- same centered width/position convention the old lone-Achievements row used. */
    private static final int START_ALONE_LEFT = 340, START_ALONE_RIGHT = 500;

    // --- Row 2 (y=370-404): Rules + Achievements, always shown ---
    private static final int ROW2_TOP = 370, ROW2_BOTTOM = 404;
    private static final int RULES_LEFT = 270, RULES_RIGHT = 390;
    private static final int ACHIEVEMENTS_LEFT = 410, ACHIEVEMENTS_RIGHT = 570;

    /** ROADMAP item 2: the "Best score / best win streak / games played" summary line, only shown once gamesPlayed > 0. */
    private static final int STATS_Y = 430;

    // --- Row 3 (y=460-494): Settings, always shown, reusing Resume's old alone-slot ---
    private static final int ROW3_TOP = 460, ROW3_BOTTOM = 494;
    private static final int SETTINGS_LEFT = 340, SETTINGS_RIGHT = 500;

    private static final int FOOTER_Y = 560;

    /** ~0.5s at 60 ticks/sec -- matches IllegalPlayFeedback/BetStepper's tick-rate assumption. */
    private static final int CURSOR_BLINK_INTERVAL_TICKS = 30;

    private volatile String name = "";
    private int frameCounter = 0;
    private boolean cursorVisible = true;

    /**
     * ROADMAP item 2: current stats, passed in by Game.runStartScreen() so
     * this screen can render its always-visible stat line. The no-arg
     * constructor (used by every pre-item-2 caller/test) defaults gamesPlayed
     * to 0, which hasStatsToShow() treats as "hide the stat line" -- exactly
     * the fresh-save state where there's nothing yet to report.
     */
    private final int gamesPlayed;
    private final int highScore;
    private final int bestWinStreakEver;

    /** ROADMAP item 10: gates both the Resume button's rendering and its click-eligibility -- see RESUME_* fields' doc. */
    private final boolean hasResumableGame;

    public StartScreen() {
        this(0, 0, 0);
    }

    public StartScreen(int gamesPlayed, int highScore, int bestWinStreakEver) {
        this(gamesPlayed, highScore, bestWinStreakEver, false);
    }

    /** ROADMAP item 10: new overload -- hasResumableGame controls whether the Resume Game button shows/is clickable. */
    public StartScreen(int gamesPlayed, int highScore, int bestWinStreakEver, boolean hasResumableGame) {
        this.gamesPlayed = gamesPlayed;
        this.highScore = highScore;
        this.bestWinStreakEver = bestWinStreakEver;
        this.hasResumableGame = hasResumableGame;
    }

    /** True once there's at least one recorded game to summarize -- gates the stat line's visibility. */
    public boolean hasStatsToShow() {
        return gamesPlayed > 0;
    }

    /**
     * Returns the control at pixel (px, py), or null if the point hits no
     * control (the name field itself, a gap, or outside every button
     * entirely). Half-open rects, same convention as BetStepper.controlAt.
     * RESUME is only ever returned when hasResumableGame is true -- a
     * not-rendered button must not still be secretly clickable. START's slot
     * shifts depending on hasResumableGame (shared row with Resume vs. alone,
     * centered).
     */
    public Control controlAt(int px, int py) {
        if (py >= ROW1_TOP && py < ROW1_BOTTOM) {
            if (hasResumableGame) {
                if (px >= RESUME_LEFT && px < RESUME_RIGHT) {
                    return Control.RESUME;
                }
                if (px >= START_WITH_RESUME_LEFT && px < START_WITH_RESUME_RIGHT) {
                    return Control.START;
                }
            } else {
                if (px >= START_ALONE_LEFT && px < START_ALONE_RIGHT) {
                    return Control.START;
                }
            }
        }
        if (py >= ROW2_TOP && py < ROW2_BOTTOM) {
            if (px >= RULES_LEFT && px < RULES_RIGHT) {
                return Control.RULES;
            }
            if (px >= ACHIEVEMENTS_LEFT && px < ACHIEVEMENTS_RIGHT) {
                return Control.ACHIEVEMENTS;
            }
        }
        if (py >= ROW3_TOP && py < ROW3_BOTTOM && px >= SETTINGS_LEFT && px < SETTINGS_RIGHT) {
            return Control.SETTINGS;
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
        if (hasResumableGame) {
            g.drawRect(RESUME_LEFT, ROW1_TOP, RESUME_RIGHT - RESUME_LEFT - 1, ROW1_BOTTOM - ROW1_TOP - 1);
            drawCenteredIn(g, "Resume Game", RESUME_LEFT, RESUME_RIGHT, ROW1_BOTTOM - 10);

            g.drawRect(START_WITH_RESUME_LEFT, ROW1_TOP, START_WITH_RESUME_RIGHT - START_WITH_RESUME_LEFT - 1, ROW1_BOTTOM - ROW1_TOP - 1);
            drawCenteredIn(g, "Start Game", START_WITH_RESUME_LEFT, START_WITH_RESUME_RIGHT, ROW1_BOTTOM - 10);
        } else {
            g.drawRect(START_ALONE_LEFT, ROW1_TOP, START_ALONE_RIGHT - START_ALONE_LEFT - 1, ROW1_BOTTOM - ROW1_TOP - 1);
            drawCenteredIn(g, "Start Game", START_ALONE_LEFT, START_ALONE_RIGHT, ROW1_BOTTOM - 10);
        }

        g.drawRect(RULES_LEFT, ROW2_TOP, RULES_RIGHT - RULES_LEFT - 1, ROW2_BOTTOM - ROW2_TOP - 1);
        drawCenteredIn(g, "Rules", RULES_LEFT, RULES_RIGHT, ROW2_BOTTOM - 10);

        g.drawRect(ACHIEVEMENTS_LEFT, ROW2_TOP, ACHIEVEMENTS_RIGHT - ACHIEVEMENTS_LEFT - 1, ROW2_BOTTOM - ROW2_TOP - 1);
        drawCenteredIn(g, "Achievements", ACHIEVEMENTS_LEFT, ACHIEVEMENTS_RIGHT, ROW2_BOTTOM - 10);

        if (hasStatsToShow()) {
            g.setColor(Color.DARK_GRAY);
            String stats = "Best score: " + highScore
                    + " · Best win streak: " + bestWinStreakEver
                    + " · Games played: " + gamesPlayed;
            drawCentered(g, stats, STATS_Y);
        }

        g.setColor(Color.BLACK);
        g.drawRect(SETTINGS_LEFT, ROW3_TOP, SETTINGS_RIGHT - SETTINGS_LEFT - 1, ROW3_BOTTOM - ROW3_TOP - 1);
        drawCenteredIn(g, "Settings", SETTINGS_LEFT, SETTINGS_RIGHT, ROW3_BOTTOM - 10);

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
