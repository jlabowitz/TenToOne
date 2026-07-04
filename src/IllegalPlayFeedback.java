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

    // ROADMAP follow-up: Rules hotspot, same geometry as
    // NextTrickPrompt's/BetStepper's (verified clear of this screen's other
    // elements, including the fade message itself -- see that item's
    // completion report for the pixel-region audit). Drawn unconditionally
    // in render(), regardless of whether a fade message is currently showing.
    private static final String RULES_LABEL = "Rules";
    private static final int RULES_TOP = 265, RULES_BOTTOM = 295;
    private static final int RULES_LEFT = 760, RULES_RIGHT = 820;

    private String text = null;
    private int elapsed = TOTAL_TICKS;  // starts "already expired"

    public void trigger(String message) {
        this.text = message;
        this.elapsed = 0;
    }

    @Override
    public void tick() {
        if (elapsed < TOTAL_TICKS) elapsed++;
    }

    @Override
    public void render(Graphics g) {
        Font defaultFont = g.getFont();

        if (text != null && elapsed < TOTAL_TICKS) {
            Color color = colorAt(elapsed);
            g.setFont(defaultFont.deriveFont(Font.BOLD));
            g.setColor(color);
            FontMetrics metrics = g.getFontMetrics();
            int x = (Game.WIDTH - metrics.stringWidth(text)) / 2;
            g.drawString(text, x, BASELINE_Y);
            g.setFont(defaultFont);
        }

        g.setColor(Color.BLACK);
        g.drawRect(RULES_LEFT, RULES_TOP, RULES_RIGHT - RULES_LEFT - 1, RULES_BOTTOM - RULES_TOP - 1);
        FontMetrics metrics = g.getFontMetrics();
        int labelWidth = metrics.stringWidth(RULES_LABEL);
        int labelX = RULES_LEFT + ((RULES_RIGHT - RULES_LEFT) - labelWidth) / 2;
        g.drawString(RULES_LABEL, labelX, RULES_BOTTOM - 10);
    }

    /**
     * Half-open rect hit-test, same convention as
     * NextTrickPrompt.isRulesHotspot/BetStepper.isRulesHotspot.
     */
    public boolean isRulesHotspot(int px, int py) {
        return px >= RULES_LEFT && px < RULES_RIGHT && py >= RULES_TOP && py < RULES_BOTTOM;
    }

    /** Pulled out as a pure static function so the timing/fade math is testable without a window. */
    static Color colorAt(int elapsed) {
        if (elapsed < HOLD_TICKS) return Color.RED;
        float f = (elapsed - HOLD_TICKS) / (float) FADE_TICKS;
        int c = Math.round(255 * f);
        return new Color(255, c, c);
    }
}
