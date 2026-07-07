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

    // ROADMAP follow-up: in-game Achievements hotspot, mirroring the Rules
    // hotspot above -- same y band, sitting to its left with a visible gap.
    // Left edge (600) is measured against this class's own longest centered
    // message ("Trump hasn't been broken yet -- lead a different suit."),
    // which ends at x=567 via headless FontMetrics measurement (bold default
    // font, centered on Game.WIDTH=840) -- 600 leaves a 33px gap from that,
    // and 730 leaves a 30px gap before RULES_LEFT (760). Sized wider (130px)
    // than the Rules box (60px) since "Achievements" (12 chars) is much
    // longer than "Rules" (5) -- see TestIllegalPlayFeedback's non-collision
    // and text-clearance tests for the geometry proof.
    private static final String ACHIEVEMENTS_LABEL = "Achievements";
    private static final int ACHIEVEMENTS_TOP = 265, ACHIEVEMENTS_BOTTOM = 295;
    private static final int ACHIEVEMENTS_LEFT = 600, ACHIEVEMENTS_RIGHT = 730;

    // ROADMAP item 10, moved to the very top of the canvas per user feedback:
    // hamburger-menu icon, top-left corner -- duplicated (not shared) across
    // BetStepper/IllegalPlayFeedback/NextTrickPrompt; see BetStepper's
    // HAMBURGER_* fields comment for the full clearance proof and the
    // documented AchievementToast-band trade-off.
    private static final int HAMBURGER_LEFT = 10, HAMBURGER_RIGHT = 40;
    private static final int HAMBURGER_TOP = 5, HAMBURGER_BOTTOM = 35;

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

        g.setColor(Color.BLACK);
        g.drawRect(RULES_LEFT, RULES_TOP, RULES_RIGHT - RULES_LEFT - 1, RULES_BOTTOM - RULES_TOP - 1);
        FontMetrics metrics = g.getFontMetrics();
        int labelWidth = metrics.stringWidth(RULES_LABEL);
        int labelX = RULES_LEFT + ((RULES_RIGHT - RULES_LEFT) - labelWidth) / 2;
        g.drawString(RULES_LABEL, labelX, RULES_BOTTOM - 10);

        g.drawRect(ACHIEVEMENTS_LEFT, ACHIEVEMENTS_TOP, ACHIEVEMENTS_RIGHT - ACHIEVEMENTS_LEFT - 1, ACHIEVEMENTS_BOTTOM - ACHIEVEMENTS_TOP - 1);
        int achievementsLabelWidth = metrics.stringWidth(ACHIEVEMENTS_LABEL);
        int achievementsLabelX = ACHIEVEMENTS_LEFT + ((ACHIEVEMENTS_RIGHT - ACHIEVEMENTS_LEFT) - achievementsLabelWidth) / 2;
        g.drawString(ACHIEVEMENTS_LABEL, achievementsLabelX, ACHIEVEMENTS_BOTTOM - 10);
    }

    /**
     * Half-open rect hit-test, same convention as
     * NextTrickPrompt.isRulesHotspot/BetStepper.isRulesHotspot.
     */
    public boolean isRulesHotspot(int px, int py) {
        return px >= RULES_LEFT && px < RULES_RIGHT && py >= RULES_TOP && py < RULES_BOTTOM;
    }

    /**
     * Half-open rect hit-test for the Achievements hotspot, same convention
     * as isRulesHotspot. See the ACHIEVEMENTS_* fields' comment for how this
     * geometry was chosen clear of the Rules box and the centered fade
     * message.
     */
    public boolean isAchievementsHotspot(int px, int py) {
        return px >= ACHIEVEMENTS_LEFT && px < ACHIEVEMENTS_RIGHT && py >= ACHIEVEMENTS_TOP && py < ACHIEVEMENTS_BOTTOM;
    }

    /**
     * Half-open rect hit-test for the hamburger-menu icon, same convention as
     * isRulesHotspot/isAchievementsHotspot -- see the HAMBURGER_* fields'
     * comment for this geometry's clearance proof.
     */
    public boolean isHamburgerHotspot(int px, int py) {
        return px >= HAMBURGER_LEFT && px < HAMBURGER_RIGHT && py >= HAMBURGER_TOP && py < HAMBURGER_BOTTOM;
    }

    /**
     * Draws the hamburger icon (three horizontal lines) inside HAMBURGER_*'s
     * bounds -- duplicated identically in BetStepper/NextTrickPrompt, same
     * convention as this class's own Rules/Achievements box rendering.
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
