import java.awt.*;

/**
 * ROADMAP item 1: the fading on-screen message shown when the human clicks
 * an illegal card (Human.playCard()'s !legal.contains(card) branch). Added
 * to the Handler once at the top of playCard() and removed in a finally --
 * same add-before/remove-after shape as BetStepper/NextTrickPrompt. Each
 * illegal click calls trigger() on the same object (not a new one per
 * click), resetting elapsed and swapping text so rapid repeated illegal
 * clicks can't stack/garble messages.
 *
 * Placed in the same verified-clear horizontal band NextTrickPrompt already
 * established (baseline y=280, bold-derived font) -- the two are never
 * shown simultaneously (sequential phases of the round loop), so there's no
 * collision risk between them.
 *
 * "Fade" is plain RGB interpolation from red toward white (this band's known
 * background color), not real alpha blending -- deliberately avoids
 * AlphaComposite, which would be new territory for this project's raw
 * Canvas/BufferStrategy render pipeline.
 *
 * Timing (60 ticks/sec): ticks [0, 30) hold solid red (~0.5s, full-legibility
 * hold before fade starts); ticks [30, 120) linearly interpolate red->white
 * (~1.5s); tick 120+ draws nothing. Total visible: 120 ticks (~2.0s).
 */
public class IllegalPlayFeedback extends GameObject {
    private static final int BASELINE_Y = 280;
    private static final int HOLD_TICKS = 30;
    private static final int FADE_TICKS = 90;
    private static final int TOTAL_TICKS = HOLD_TICKS + FADE_TICKS;

    // ROADMAP item 10, moved to the very top of the canvas per user feedback:
    // hamburger-menu icon, top-left corner -- duplicated (not shared) across
    // BetStepper/IllegalPlayFeedback/NextTrickPrompt; see BetStepper's
    // HAMBURGER_* fields comment for the full clearance proof and the
    // documented AchievementToast-band trade-off. Rules/Achievements are
    // reached only through the hamburger dropdown now (HamburgerMenu) -- the
    // dedicated per-class Rules/Achievements hotspot boxes this class used to
    // render/hit-test were removed in the follow-up polish pass that added
    // the dropdown.
    private static final int HAMBURGER_LEFT = 10, HAMBURGER_RIGHT = 40;
    private static final int HAMBURGER_TOP = 5, HAMBURGER_BOTTOM = 35;

    private String text = null;
    private int elapsed = TOTAL_TICKS;  // starts "already expired"

    public void trigger(String message) {
        this.text = message;
        this.elapsed = 0;
        InteractionLog.logShown("IllegalPlayFeedback: " + message);
    }

    @Override
    public void tick() {
        if (elapsed < TOTAL_TICKS) elapsed++;
    }

    @Override
    public void render(Graphics g) {
        Font defaultFont = g.getFont();

        renderHamburgerIcon(g);

        if (text != null && elapsed < TOTAL_TICKS) {
            Color color = colorAt(elapsed);
            g.setFont(defaultFont.deriveFont(Font.BOLD));
            g.setColor(color);
            FontMetrics metrics = g.getFontMetrics();
            int x = (Game.WIDTH - metrics.stringWidth(text)) / 2;
            g.drawString(text, x, BASELINE_Y);
            g.setFont(defaultFont);
        }
    }

    /**
     * Half-open rect hit-test for the hamburger-menu icon, same convention as
     * BetStepper.isHamburgerHotspot -- see the HAMBURGER_* fields' comment
     * for this geometry's clearance proof. Rules/Achievements are reached
     * only through the hamburger dropdown now (HamburgerMenu) -- this class
     * no longer has its own dedicated Rules/Achievements hotspots.
     */
    public boolean isHamburgerHotspot(int px, int py) {
        return px >= HAMBURGER_LEFT && px < HAMBURGER_RIGHT && py >= HAMBURGER_TOP && py < HAMBURGER_BOTTOM;
    }

    /**
     * Draws the hamburger icon (three horizontal lines) inside HAMBURGER_*'s
     * bounds -- duplicated identically in BetStepper/NextTrickPrompt.
     */
    private static void renderHamburgerIcon(Graphics g) {
        g.setColor(Color.BLACK);
        int lineLeft = HAMBURGER_LEFT + 4;
        int lineRight = HAMBURGER_RIGHT - 4;
        int gap = (HAMBURGER_BOTTOM - HAMBURGER_TOP) / 4;
        for (int i = 1; i <= 3; i++) {
            int lineY = HAMBURGER_TOP + gap * i;
            g.drawLine(lineLeft, lineY, lineRight, lineY);
        }
    }

    /** Pulled out as a pure static function so the timing/fade math is testable without a window. */
    static Color colorAt(int elapsed) {
        if (elapsed < HOLD_TICKS) return Color.RED;
        float f = (elapsed - HOLD_TICKS) / (float) FADE_TICKS;
        int c = Math.round(255 * f);
        return new Color(255, c, c);
    }
}
