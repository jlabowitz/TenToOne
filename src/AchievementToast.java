import java.awt.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ROADMAP item 2: the mid-session achievement-unlock toast. Originally reused
 * IllegalPlayFeedback's hold/fade timing shape (30 ticks solid hold + 90
 * ticks fade); user feedback after hands-on testing was that this expired
 * before there was time to read it, so this class now holds much longer --
 * 240 ticks solid hold + 120 ticks fade = 360 total, ~6s at 60 ticks/sec (~4s
 * fully solid, ~2s fading) -- while still backed by a FIFO queue instead of a
 * single overwrite-in-place slot -- two achievements can unlock in the same
 * instant (e.g. a score threshold and a round-bonus threshold both crossing
 * on the same round-end) and both must display, one after another, never
 * silently dropped. The queue is a LinkedBlockingQueue, same
 * as MouseInput's cross-thread queue: enqueue() is called from the
 * game-logic thread (Game's round-end/game-end/name-submission hooks) and
 * tick() dequeues (via the non-blocking poll(), since a render-thread tick
 * must never block waiting for an item) from the render/tick thread -- a
 * plain ArrayDeque would not be safe to share across that boundary.
 *
 * Lifecycle (see Game's class doc / this item's completion report for the
 * full rationale): instantiated once per Game (long-lived, mirroring how
 * players stay registered across Play-Again) and registered with the
 * Handler via keepOnTop() immediately, before the Start Screen is even
 * shown -- so an achievement queued during the Start Screen (the Bapi
 * easter egg fires at name-submission time, before any Round exists) is
 * never dropped by a construct-late/remove-early lifecycle. keepOnTop()
 * (see Handler's class doc) keeps this object re-bumped to the end of the
 * Handler's list -- and therefore rendered last -- every time anything else
 * is added, so it stays visually on top of whatever's currently showing
 * (round summary panel, game-over banner, rules view, achievements view,
 * gameplay itself) for its entire lifetime, not just whichever screen
 * happened to exist when it was first added.
 *
 * It only actually ticks/renders once active (set via activate(), called by
 * Game.play() once real gameplay begins) -- before that, enqueue() still
 * accepts items, they just wait. This is what keeps an unlock queued during
 * the Start Screen (the Bapi easter egg) from appearing prematurely over
 * the Start Screen itself -- not render order (which now always favors this
 * object) -- and it appears on the very first frame once activate() flips
 * the switch.
 *
 * Rendered as a plain centered bold banner near the top of the canvas
 * (y in [BOX_TOP, BOX_BOTTOM)), a region nothing else in ordinary gameplay
 * draws into (AI name/HUD text starts at y=50, and none of the per-round
 * overlays -- BetStepper, IllegalPlayFeedback, NextTrickPrompt -- reach this
 * high on the canvas) -- though the full-canvas modals do paint over that
 * whole band too, which is exactly why keepOnTop() (not this object's
 * y-range) is what actually keeps it visible over them.
 *
 * ROADMAP follow-up: click-dismissible, not just auto-fade -- with the hold
 * duration above now generous, a player who's already read the message
 * shouldn't be stuck waiting ~6s for it to clear on its own. isToastHotspot()
 * uses that same known-dead top band (y < TOAST_BAND_BOTTOM, full canvas
 * width) as a big, forgiving click target rather than tracking the rendered
 * text's exact (centered, variable-width) bounds -- deliberately generous,
 * not tight, and only ever true while something is actually showing (gated
 * on isShowingSomething()) so it doesn't silently eat clicks aimed at
 * whatever's underneath when there's nothing queued. dismiss() ends the
 * current display immediately -- same as if the timer had expired -- so
 * tick() naturally advances to the next queued item (if any) on the very
 * next call, same check-then-increment shape as an ordinary expiry. This is
 * deliberately NOT a "must click to continue" modal: callers (Human's click
 * loops) treat a toast-dismiss click as consumed and continue their own
 * loop, so achievements keep queuing/displaying during ordinary gameplay
 * without forcing an extra click every time one appears.
 */
public class AchievementToast extends GameObject {
    /** Same RGB as ModalOverlay.GOLD -- this project's "success" visual language. Duplicated as a literal for the same reason ModalOverlay itself duplicates Card.HIGH_CARD_COLOR (see that class's doc). */
    static final Color GOLD = new Color(204, 153, 0);

    static final int HOLD_TICKS = 240;
    static final int FADE_TICKS = 120;
    static final int TOTAL_TICKS = HOLD_TICKS + FADE_TICKS;

    private static final int BASELINE_Y = 30;

    /**
     * Click-dismiss target's bottom edge (exclusive) -- matches the class
     * doc's "AI name/HUD text starts at y=50" dead-space boundary. Full
     * canvas width, y in [0, TOAST_BAND_BOTTOM); see isToastHotspot().
     */
    static final int TOAST_BAND_BOTTOM = 50;

    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private volatile boolean active = false;

    private String currentText = null;
    private int elapsed = TOTAL_TICKS; // starts "already expired" -- nothing queued yet

    /** Starts actual ticking/rendering -- see class doc on why this is gated separately from construction. */
    public void activate() {
        active = true;
    }

    public void enqueue(String message) {
        queue.offer(message);
    }

    /** Convenience for a single newly-unlocked achievement -- formats the toast text centrally so every call site stays consistent. */
    public void enqueue(Achievement achievement) {
        enqueue("Achievement Unlocked: " + achievement.getDisplayName());
    }

    /** Convenience for enqueuing every achievement in a batch (e.g. AchievementEngine's newly-unlocked list) in order. */
    public void enqueueAll(List<Achievement> achievements) {
        for (Achievement achievement : achievements) {
            enqueue(achievement);
        }
    }

    /** Test-only introspection: whether a message is currently mid-display (not expired, not empty-queue). */
    boolean isShowingSomething() {
        return currentText != null && elapsed < TOTAL_TICKS;
    }

    /** Test-only introspection: the text currently being displayed, or null. */
    String getCurrentText() {
        return isShowingSomething() ? currentText : null;
    }

    /**
     * Half-open-rect hit-test for the click-to-dismiss target, same
     * convention as BetStepper.controlAt/isHamburgerHotspot -- but a big,
     * generous top-of-canvas band (see TOAST_BAND_BOTTOM) rather than a tight
     * bound on the rendered text, since that text is centered and
     * variable-width. Gated on isShowingSomething() so a click up here is
     * only ever treated as a dismiss while a message is actually displayed --
     * with nothing queued, this band is just the dead space the class doc
     * already describes, and a click there falls through to whatever's
     * underneath.
     */
    public boolean isToastHotspot(int px, int py) {
        return isShowingSomething() && px >= 0 && px < Game.WIDTH && py >= 0 && py < TOAST_BAND_BOTTOM;
    }

    /**
     * Ends the current display immediately, same as if the timer had
     * expired -- tick()'s check-then-increment shape means the queue
     * naturally advances to the next item (if any) on the very next tick(),
     * no separate "skip" path needed.
     */
    public void dismiss() {
        elapsed = TOTAL_TICKS;
    }

    @Override
    public void tick() {
        if (!active) {
            return;
        }
        if (currentText == null || elapsed >= TOTAL_TICKS) {
            currentText = queue.poll(); // null if the queue is empty -- fine, isShowingSomething() handles it
            elapsed = 0;
            if (currentText != null) {
                InteractionLog.logShown("AchievementToast: " + currentText);
            }
        } else {
            elapsed++;
        }
    }

    @Override
    public void render(Graphics g) {
        if (!active || !isShowingSomething()) {
            return;
        }
        Font defaultFont = g.getFont();
        g.setFont(defaultFont.deriveFont(Font.BOLD));
        g.setColor(colorAt(elapsed));
        FontMetrics metrics = g.getFontMetrics();
        int x = (Game.WIDTH - metrics.stringWidth(currentText)) / 2;
        g.drawString(currentText, x, BASELINE_Y);
        g.setFont(defaultFont);
    }

    /** Pulled out as a pure static function so the timing/fade math is testable without a window -- mirrors IllegalPlayFeedback.colorAt. */
    static Color colorAt(int elapsed) {
        if (elapsed < HOLD_TICKS) {
            return GOLD;
        }
        float f = (elapsed - HOLD_TICKS) / (float) FADE_TICKS;
        int r = GOLD.getRed() + Math.round((255 - GOLD.getRed()) * f);
        int gr = GOLD.getGreen() + Math.round((255 - GOLD.getGreen()) * f);
        int b = GOLD.getBlue() + Math.round((255 - GOLD.getBlue()) * f);
        return new Color(r, gr, b);
    }
}
