import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * ROADMAP item 1: the pre-launch splash screen -- title, subtitle, name-entry
 * field, and Rules/New Game buttons. A full 840x630 canvas GameObject, same
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
 * ROADMAP item 10 follow-up (this pass): several changes on top of the
 * earlier three-row layout --
 *
 * (1) Every two-button row (Resume/Start, Rules/Achievements, Stats/Settings)
 * now shares the same equal-width/20px-gap/centered geometry: both buttons in
 * a row are PAIR_BUTTON_WIDTH (140px) wide, split across the same 270-570
 * span the name field itself spans (FIELD_LEFT/RIGHT), with a PAIR_GAP (20px)
 * gap between -- so all rows visually align. "Achievements" (the longest of
 * the six button labels) was verified via FontMetrics to fit comfortably
 * inside 140px before this was finalized (77px rendered width against Dialog
 * 12pt plain, the font actually in effect at that draw call -- see
 * TestStartScreen's own fitting proof) -- no full-width fallback was needed.
 *
 * (2) Row 3 gained a new "Stats" button alongside Settings, replacing the old
 * inline "Best score / best win streak / games played" text line entirely --
 * clicking it opens the new StatsView modal (reads the live SaveData
 * directly, same as AchievementsView already does, so this class doesn't
 * need to carry every individual stat field through its own constructor).
 * Hidden entirely when there's nothing to show yet (gamesPlayed == 0),
 * mirroring hasResumableGame's own gating of the Resume button -- when
 * hidden, Settings reoccupies the same centered alone-slot Start uses when
 * Resume is absent (SETTINGS_ALONE_LEFT/RIGHT).
 *
 * (3) The "Your name:" label and the "Up to N characters..." footer line are
 * gone entirely; the empty-field placeholder changed from "e.g. Alex" to
 * "Nickname here". The name buffer now starts pre-populated from
 * SaveData.lastUsedName (threaded in via the constructor, persisted by
 * Game.runStartScreen() whenever a name is actually submitted) instead of
 * always starting empty.
 *
 * (4) Ctrl+A (select-all) and Ctrl+Backspace (delete-previous-word), plus
 * Enter-to-submit, per TypingTarget's new selectAll()/deleteWord()/submit()
 * methods -- see each method's own doc for how they're modeled against a
 * single plain String buffer rather than a real text-editing widget.
 */
public class StartScreen extends GameObject implements TypingTarget {
    public enum Control { RULES, START, ACHIEVEMENTS, RESUME, SETTINGS, STATS }

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

    private static final int FIELD_TOP = 260;
    private static final int FIELD_BOTTOM = 294;
    private static final int FIELD_LEFT = 270;
    private static final int FIELD_RIGHT = 570;

    private static final int FIELD_TEXT_X = FIELD_LEFT + 10;
    private static final int FIELD_TEXT_Y = FIELD_BOTTOM - 10;

    /**
     * ROADMAP item 10 follow-up: shared geometry every two-button row (Row 1
     * Resume/Start, Row 2 Rules/Achievements, Row 3 Stats/Settings) is built
     * from -- both slots in a row are always PAIR_BUTTON_WIDTH wide with
     * PAIR_GAP between, spanning the same PAIR_LEFT..PAIR_RIGHT range the
     * name field itself spans. See class doc for the FontMetrics check that
     * justified 140px (comfortably fits "Achievements", the longest label).
     */
    private static final int PAIR_LEFT = FIELD_LEFT, PAIR_RIGHT = FIELD_RIGHT;
    private static final int PAIR_GAP = 20;
    private static final int PAIR_BUTTON_WIDTH = ((PAIR_RIGHT - PAIR_LEFT) - PAIR_GAP) / 2;
    private static final int PAIR_A_LEFT = PAIR_LEFT, PAIR_A_RIGHT = PAIR_LEFT + PAIR_BUTTON_WIDTH;
    private static final int PAIR_B_LEFT = PAIR_A_RIGHT + PAIR_GAP, PAIR_B_RIGHT = PAIR_RIGHT;

    // --- Row 1 (y=320-354): Resume Game + New Game, or New Game alone ---
    private static final int ROW1_TOP = 320, ROW1_BOTTOM = 354;

    /** Resume's slot -- only rendered/clickable when hasResumableGame is true. */
    private static final int RESUME_LEFT = PAIR_A_LEFT, RESUME_RIGHT = PAIR_A_RIGHT;

    /** Start's slot when Resume is also shown. */
    private static final int START_WITH_RESUME_LEFT = PAIR_B_LEFT, START_WITH_RESUME_RIGHT = PAIR_B_RIGHT;

    /** Start's slot when shown alone -- same centered width/position convention the old lone-Achievements row used. */
    private static final int START_ALONE_LEFT = 340, START_ALONE_RIGHT = 500;

    // --- Row 2 (y=370-404): Rules + Achievements, always shown ---
    private static final int ROW2_TOP = 370, ROW2_BOTTOM = 404;
    private static final int RULES_LEFT = PAIR_A_LEFT, RULES_RIGHT = PAIR_A_RIGHT;
    private static final int ACHIEVEMENTS_LEFT = PAIR_B_LEFT, ACHIEVEMENTS_RIGHT = PAIR_B_RIGHT;

    // --- Row 3 (y=420-454): Stats + Settings when there's something to show stats for, else Settings alone ---
    // Code-review fix: ROW3_TOP used to be 460 (a 56px gap after Row 2's
    // ROW2_BOTTOM=404, a leftover from when the old inline stats text line
    // used to occupy that space before the Stats button replaced it).
    // Tightened to the same 16px gap every other row pair uses
    // (ROW1_BOTTOM..ROW2_TOP and ROW2_BOTTOM..ROW3_TOP are both now 16px),
    // keeping the same 34px row height.
    private static final int ROW3_TOP = ROW2_BOTTOM + 16, ROW3_BOTTOM = ROW3_TOP + 34;
    private static final int STATS_LEFT = PAIR_A_LEFT, STATS_RIGHT = PAIR_A_RIGHT;
    private static final int SETTINGS_WITH_STATS_LEFT = PAIR_B_LEFT, SETTINGS_WITH_STATS_RIGHT = PAIR_B_RIGHT;

    /** Settings' slot when shown alone (no stats yet) -- reuses the exact slot Settings alone used to occupy pre-this-pass. */
    private static final int SETTINGS_ALONE_LEFT = 340, SETTINGS_ALONE_RIGHT = 500;

    /** ~0.5s at 60 ticks/sec -- matches IllegalPlayFeedback/BetStepper's tick-rate assumption. */
    private static final int CURSOR_BLINK_INTERVAL_TICKS = 30;

    /** ROADMAP item 10 follow-up: the fill color for a select-all highlight, standard text-field selection blue. */
    private static final Color SELECTION_HIGHLIGHT = new Color(51, 153, 255);

    /**
     * Headless FontMetrics for the Dialog-12-plain default font render()
     * draws the name field in (same technique this class's own
     * MAX_NAME_LENGTH doc, and TestStartScreen's own fitting proofs, already
     * measure against) -- needed by clickNameField()/nearestCharBoundary(),
     * which run on the game-logic thread (Game.runStartScreen()'s click
     * loop) and so have no live Graphics context of their own to measure
     * against.
     */
    private static final FontMetrics NAME_FIELD_METRICS =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics().getFontMetrics();

    private volatile String name;

    /**
     * ROADMAP item 10 follow-up (second pass): the insertion point within
     * `name`, in [0, name.length()]. typeChar inserts here (not just
     * appends), backspace/deleteWord delete relative to here, and
     * moveCursorLeft/Right/clickNameField move it -- a real (if simplified:
     * no multi-char selection range beyond "all") cursor, replacing the
     * first pass's append-only model after user feedback that select-all had
     * no visible indicator and arrow keys/click didn't work at all.
     */
    private volatile int cursorPos;

    /**
     * ROADMAP item 10 follow-up: true while the whole buffer is selected
     * (Ctrl+A) -- standard select-all-then-type-replaces convention: the
     * *next* typeChar/backspace/deleteWord clears the whole buffer first
     * (see each method's own body), and a Left/Right arrow or a field click
     * instead collapses the selection to a cursor position (start, end, or
     * the clicked point respectively) without deleting anything, matching
     * every real text field's own convention. render() shows this as a
     * filled highlight behind the text (no blinking caret) instead of
     * leaving it invisible -- the first pass's silent flag was exactly the
     * user-reported confusion this second pass fixes.
     */
    private volatile boolean allSelected = false;

    /**
     * ROADMAP item 10 follow-up: set by submit() (KeyInput routes Enter here,
     * see TypingTarget's doc), consumed by Game.runStartScreen()'s polling
     * loop via consumeSubmitRequested() -- a plain read-and-clear flag rather
     * than a queue, since at most one Enter-submit can be pending at a time
     * and losing/coalescing a rapid double-Enter is fine (matches this
     * codebase's existing MouseInput.clearClicks()-between-views precedent
     * of not caring about stale/duplicate input across a screen transition).
     */
    private volatile boolean submitRequested = false;

    /**
     * ROADMAP item 10 follow-up (fourth pass): volatile, not plain fields --
     * tick() (render-thread) still owns the actual blink cadence, but every
     * cursor-moving TypingTarget method (typeChar/backspace/deleteWord/
     * moveCursorLeft/Right/moveWordLeft/Right, plus clickNameField) now also
     * resets these via resetCursorBlink() from whichever thread called it
     * (KeyInput's AWT event thread, or Game.runStartScreen()'s game-logic
     * thread for a mouse click) -- standard OS text-field convention where
     * the caret stays solid while you're actively moving/editing instead of
     * blinking mid-navigation, only resuming the blink cycle once input goes
     * quiet. Same write-one-thread(s)/read-another volatile pattern this
     * class already uses for name/cursorPos/allSelected.
     */
    private volatile int frameCounter = 0;
    private volatile boolean cursorVisible = true;

    /**
     * ROADMAP item 2: current stats, passed in by Game.runStartScreen() so
     * this screen can gate its Stats button's visibility. The no-arg
     * constructor (used by every pre-item-2 caller/test) defaults gamesPlayed
     * to 0, which hasStatsToShow() treats as "hide the Stats button" --
     * exactly the fresh-save state where there's nothing yet to report.
     */
    private final int gamesPlayed;

    /** ROADMAP item 10: gates both the Resume button's rendering and its click-eligibility -- see RESUME_* fields' doc. */
    private final boolean hasResumableGame;

    public StartScreen() {
        this(0);
    }

    public StartScreen(int gamesPlayed) {
        this(gamesPlayed, false);
    }

    /** ROADMAP item 10: new overload -- hasResumableGame controls whether the Resume Game button shows/is clickable. */
    public StartScreen(int gamesPlayed, boolean hasResumableGame) {
        this(gamesPlayed, hasResumableGame, "");
    }

    /**
     * ROADMAP item 10 follow-up: new overload -- lastUsedName pre-populates
     * the name buffer (instead of always starting empty), sourced from
     * SaveData.lastUsedName by Game.runStartScreen(). Defensively clamped to
     * MAX_NAME_LENGTH and null-coalesced to "" in case a hand-edited save
     * file ever supplied something typeChar's own cap could never have
     * produced -- this class must never start in a state typing alone
     * couldn't reach.
     */
    public StartScreen(int gamesPlayed, boolean hasResumableGame, String lastUsedName) {
        this.gamesPlayed = gamesPlayed;
        this.hasResumableGame = hasResumableGame;
        String initial = lastUsedName == null ? "" : lastUsedName;
        this.name = initial.length() > MAX_NAME_LENGTH ? initial.substring(0, MAX_NAME_LENGTH) : initial;
        this.cursorPos = this.name.length();
    }

    /** True once there's at least one recorded game to summarize -- gates the Stats button's visibility. */
    public boolean hasStatsToShow() {
        return gamesPlayed > 0;
    }

    /**
     * Returns the control at pixel (px, py), or null if the point hits no
     * control (the name field itself, a gap, or outside every button
     * entirely). Half-open rects, same convention as BetStepper.controlAt.
     * RESUME is only ever returned when hasResumableGame is true, and STATS
     * only when hasStatsToShow() is true -- a not-rendered button must not
     * still be secretly clickable. START's and SETTINGS' slots each shift
     * depending on whether their row-mate is also shown (shared paired slot
     * vs. alone, centered).
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
        if (py >= ROW3_TOP && py < ROW3_BOTTOM) {
            if (hasStatsToShow()) {
                if (px >= STATS_LEFT && px < STATS_RIGHT) {
                    return Control.STATS;
                }
                if (px >= SETTINGS_WITH_STATS_LEFT && px < SETTINGS_WITH_STATS_RIGHT) {
                    return Control.SETTINGS;
                }
            } else {
                if (px >= SETTINGS_ALONE_LEFT && px < SETTINGS_ALONE_RIGHT) {
                    return Control.SETTINGS;
                }
            }
        }
        return null;
    }

    @Override
    public void typeChar(char c) {
        if (allSelected) {
            name = "";
            cursorPos = 0;
            allSelected = false;
        }
        if (name.length() < MAX_NAME_LENGTH) {
            name = name.substring(0, cursorPos) + c + name.substring(cursorPos);
            cursorPos++;
        }
        resetCursorBlink();
    }

    @Override
    public void backspace() {
        if (allSelected) {
            name = "";
            cursorPos = 0;
            allSelected = false;
            resetCursorBlink();
            return;
        }
        if (cursorPos > 0) {
            name = name.substring(0, cursorPos - 1) + name.substring(cursorPos);
            cursorPos--;
        }
        resetCursorBlink();
    }

    @Override
    public void selectAll() {
        allSelected = true;
    }

    /**
     * ROADMAP item 10 follow-up: removes the run of non-space characters
     * immediately before the cursor, plus any whitespace before that, from
     * the buffer -- standard Ctrl+Backspace word-delete semantics relative
     * to the cursor (e.g. cursor at the end of "Alex Smith" -> "Alex "),
     * leaving anything after the cursor untouched. Kept intentionally simple
     * given names are short, single-token-ish strings anyway rather than a
     * real word-boundary/locale-aware implementation.
     */
    @Override
    public void deleteWord() {
        if (allSelected) {
            name = "";
            cursorPos = 0;
            allSelected = false;
            resetCursorBlink();
            return;
        }
        String before = name.substring(0, cursorPos);
        String after = name.substring(cursorPos);
        String trimmedBefore = deleteTrailingWord(before);
        name = trimmedBefore + after;
        cursorPos = trimmedBefore.length();
        resetCursorBlink();
    }

    /** Pulled out as a pure static function so it's directly unit-testable -- mirrors KeyInput.isAllowedNameChar's precedent. */
    static String deleteTrailingWord(String s) {
        int i = s.length();
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(s.charAt(i - 1))) {
            i--;
        }
        return s.substring(0, i);
    }

    @Override
    public void moveCursorLeft() {
        if (allSelected) {
            allSelected = false;
            cursorPos = 0;
            resetCursorBlink();
            return;
        }
        if (cursorPos > 0) {
            cursorPos--;
        }
        resetCursorBlink();
    }

    @Override
    public void moveCursorRight() {
        if (allSelected) {
            allSelected = false;
            cursorPos = name.length();
            resetCursorBlink();
            return;
        }
        if (cursorPos < name.length()) {
            cursorPos++;
        }
        resetCursorBlink();
    }

    /**
     * ROADMAP item 10 follow-up (third pass): Ctrl+Left, standard OS
     * word-jump convention -- jumps to the start of the previous word
     * instead of moving one character. A selection collapses to the start
     * first (same convention moveCursorLeft already uses), same as every
     * real text field's own Ctrl+Left-with-a-selection behavior.
     */
    @Override
    public void moveWordLeft() {
        if (allSelected) {
            allSelected = false;
            cursorPos = 0;
            resetCursorBlink();
            return;
        }
        cursorPos = previousWordBoundary(name, cursorPos);
        resetCursorBlink();
    }

    /** ROADMAP item 10 follow-up (third pass): Ctrl+Right, the mirror of moveWordLeft -- jumps to the end of the next word. */
    @Override
    public void moveWordRight() {
        if (allSelected) {
            allSelected = false;
            cursorPos = name.length();
            resetCursorBlink();
            return;
        }
        cursorPos = nextWordBoundary(name, cursorPos);
        resetCursorBlink();
    }

    /**
     * Returns the index of the start of the word run ending at or before
     * POS -- skips any whitespace immediately before POS, then the run of
     * non-whitespace before that. Same index math as deleteTrailingWord,
     * but as a pure position lookup (no deletion) so moveWordLeft can reuse
     * it against an arbitrary cursor position, not just the end of the
     * buffer. Pulled out as a pure static function for the same
     * directly-unit-testable reason as deleteTrailingWord/isAllowedNameChar.
     */
    static int previousWordBoundary(String s, int pos) {
        int i = pos;
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(s.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /** Mirror of previousWordBoundary: returns the index just past the end of the word run starting at or after POS. */
    static int nextWordBoundary(String s, int pos) {
        int i = pos;
        int len = s.length();
        while (i < len && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        while (i < len && !Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Half-open rect hit-test for the name field itself -- used by Game.runStartScreen() to route a click there to clickNameField() instead of "no control matched". */
    public boolean isNameField(int px, int py) {
        return px >= FIELD_LEFT && px < FIELD_RIGHT && py >= FIELD_TOP && py < FIELD_BOTTOM;
    }

    /**
     * ROADMAP item 10 follow-up: places the cursor at the character boundary
     * nearest PX (standard click-to-position convention) and clears any
     * active selection -- called from Game.runStartScreen()'s click-handling
     * loop (the game-logic thread), which has no live Graphics context of
     * its own, hence NAME_FIELD_METRICS below rather than measuring against
     * whatever Graphics render() was last called with.
     */
    public void clickNameField(int px) {
        allSelected = false;
        cursorPos = nearestCharBoundary(px - FIELD_TEXT_X);
        resetCursorBlink();
    }

    /** Returns the index in [0, name.length()] whose rendered x-position is nearest RELATIVEX -- each candidate boundary is judged by the midpoint to its neighbor, same rounding convention every real text field uses. */
    private int nearestCharBoundary(int relativeX) {
        if (relativeX <= 0) {
            return 0;
        }
        int previousWidth = 0;
        for (int i = 1; i <= name.length(); i++) {
            int width = NAME_FIELD_METRICS.stringWidth(name.substring(0, i));
            if (relativeX < (previousWidth + width) / 2) {
                return i - 1;
            }
            previousWidth = width;
        }
        return name.length();
    }

    @Override
    public void submit() {
        submitRequested = true;
    }

    /**
     * Game.runStartScreen()'s seam (package-private, not on the TypingTarget
     * interface -- Game already holds this concrete StartScreen reference
     * directly, unlike KeyInput which only ever sees the interface type):
     * reads and clears the pending Enter-submit flag in one step, so a
     * second poll never re-fires the same Enter press.
     */
    boolean consumeSubmitRequested() {
        if (submitRequested) {
            submitRequested = false;
            return true;
        }
        return false;
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

    /**
     * ROADMAP item 10 follow-up (fourth pass): standard OS text-field
     * convention -- any cursor-moving action (typing, Backspace/Ctrl+
     * Backspace, arrow keys, Ctrl+arrow word-jump, or a field click) keeps
     * the caret solid instead of blinking mid-navigation, only resuming the
     * blink cycle once input goes quiet again. Called from every
     * TypingTarget method that changes cursorPos or the buffer, plus
     * clickNameField -- see frameCounter/cursorVisible's own doc for why
     * they're volatile now that this runs from other threads, not just
     * tick()'s render thread.
     */
    private void resetCursorBlink() {
        cursorVisible = true;
        frameCounter = 0;
    }

    /** Test-observation seam (package-private, mirrors consumeSubmitRequested's own convention): whether the blinking caret is currently in its visible phase. */
    boolean isCursorBlinkVisible() {
        return cursorVisible;
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

        g.setColor(Color.BLACK);
        g.drawRect(FIELD_LEFT, FIELD_TOP, FIELD_RIGHT - FIELD_LEFT - 1, FIELD_BOTTOM - FIELD_TOP - 1);

        String typedText = name;
        FontMetrics fieldMetrics = g.getFontMetrics();
        if (typedText.isEmpty()) {
            g.setFont(defaultFont.deriveFont(Font.ITALIC));
            g.setColor(Color.GRAY);
            g.drawString("Nickname here", FIELD_TEXT_X, FIELD_TEXT_Y);
            g.setFont(defaultFont);
        } else if (allSelected) {
            //ROADMAP item 10 follow-up: user feedback was that select-all had
            //no visible indicator -- a filled highlight behind the text,
            //standard text-field convention, replaces the silent flag the
            //first pass shipped with.
            int textWidth = fieldMetrics.stringWidth(typedText);
            g.setColor(SELECTION_HIGHLIGHT);
            g.fillRect(FIELD_TEXT_X, FIELD_TOP + 4, textWidth, FIELD_BOTTOM - FIELD_TOP - 8);
            g.setColor(Color.WHITE);
            g.drawString(typedText, FIELD_TEXT_X, FIELD_TEXT_Y);
        } else {
            g.setColor(Color.BLACK);
            g.drawString(typedText, FIELD_TEXT_X, FIELD_TEXT_Y);
        }

        if (cursorVisible && !allSelected) {
            //ROADMAP item 10 follow-up: cursor now renders at cursorPos, not
            //always at the end of the buffer, now that typing/arrow keys/
            //clicks can move it anywhere within the text.
            int cursorX = FIELD_TEXT_X + fieldMetrics.stringWidth(typedText.substring(0, cursorPos));
            g.setColor(Color.BLACK);
            g.drawLine(cursorX, FIELD_TOP + 6, cursorX, FIELD_BOTTOM - 6);
        }

        g.setColor(Color.BLACK);
        if (hasResumableGame) {
            g.drawRect(RESUME_LEFT, ROW1_TOP, RESUME_RIGHT - RESUME_LEFT - 1, ROW1_BOTTOM - ROW1_TOP - 1);
            drawCenteredIn(g, "Resume Game", RESUME_LEFT, RESUME_RIGHT, ROW1_BOTTOM - 10);

            g.drawRect(START_WITH_RESUME_LEFT, ROW1_TOP, START_WITH_RESUME_RIGHT - START_WITH_RESUME_LEFT - 1, ROW1_BOTTOM - ROW1_TOP - 1);
            drawCenteredIn(g, "New Game", START_WITH_RESUME_LEFT, START_WITH_RESUME_RIGHT, ROW1_BOTTOM - 10);
        } else {
            g.drawRect(START_ALONE_LEFT, ROW1_TOP, START_ALONE_RIGHT - START_ALONE_LEFT - 1, ROW1_BOTTOM - ROW1_TOP - 1);
            drawCenteredIn(g, "New Game", START_ALONE_LEFT, START_ALONE_RIGHT, ROW1_BOTTOM - 10);
        }

        g.drawRect(RULES_LEFT, ROW2_TOP, RULES_RIGHT - RULES_LEFT - 1, ROW2_BOTTOM - ROW2_TOP - 1);
        drawCenteredIn(g, "Rules", RULES_LEFT, RULES_RIGHT, ROW2_BOTTOM - 10);

        g.drawRect(ACHIEVEMENTS_LEFT, ROW2_TOP, ACHIEVEMENTS_RIGHT - ACHIEVEMENTS_LEFT - 1, ROW2_BOTTOM - ROW2_TOP - 1);
        drawCenteredIn(g, "Achievements", ACHIEVEMENTS_LEFT, ACHIEVEMENTS_RIGHT, ROW2_BOTTOM - 10);

        if (hasStatsToShow()) {
            g.drawRect(STATS_LEFT, ROW3_TOP, STATS_RIGHT - STATS_LEFT - 1, ROW3_BOTTOM - ROW3_TOP - 1);
            drawCenteredIn(g, "Stats", STATS_LEFT, STATS_RIGHT, ROW3_BOTTOM - 10);

            g.drawRect(SETTINGS_WITH_STATS_LEFT, ROW3_TOP, SETTINGS_WITH_STATS_RIGHT - SETTINGS_WITH_STATS_LEFT - 1, ROW3_BOTTOM - ROW3_TOP - 1);
            drawCenteredIn(g, "Settings", SETTINGS_WITH_STATS_LEFT, SETTINGS_WITH_STATS_RIGHT, ROW3_BOTTOM - 10);
        } else {
            g.drawRect(SETTINGS_ALONE_LEFT, ROW3_TOP, SETTINGS_ALONE_RIGHT - SETTINGS_ALONE_LEFT - 1, ROW3_BOTTOM - ROW3_TOP - 1);
            drawCenteredIn(g, "Settings", SETTINGS_ALONE_LEFT, SETTINGS_ALONE_RIGHT, ROW3_BOTTOM - 10);
        }
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
