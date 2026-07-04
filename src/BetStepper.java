import java.awt.*;

/**
 * Composite GameObject for the mouse-driven bet-input stepper:
 * [ − ][ value ][ + ][ Bet ], all sharing y in [TOP, BOTTOM).
 *
 * Owns a draft bet value (clamped to [0, maxBet], inert no-op at either
 * boundary) and a single hit-test method. Added to the Handler on entry to
 * Human.bet() and removed on return -- it doesn't persist across rounds, so
 * its geometry is fixed rather than derived from any per-round state other
 * than maxBet.
 */
public class BetStepper extends GameObject {
    public enum Control { DECREMENT, INCREMENT, BET }

    private static final int TOP = 585;
    private static final int BOTTOM = 619;

    private static final int DECREMENT_LEFT = 620;
    private static final int DECREMENT_RIGHT = 650;

    private static final int VALUE_LEFT = 655;
    private static final int VALUE_RIGHT = 695;

    private static final int INCREMENT_LEFT = 700;
    private static final int INCREMENT_RIGHT = 730;

    private static final int BET_LEFT = 740;
    private static final int BET_RIGHT = 800;

    // ROADMAP follow-up: Rules hotspot, same geometry as
    // NextTrickPrompt's (verified clear of this screen's other elements --
    // see that item's completion report for the pixel-region audit).
    private static final String RULES_LABEL = "Rules";
    private static final int RULES_TOP = 265, RULES_BOTTOM = 295;
    private static final int RULES_LEFT = 760, RULES_RIGHT = 820;

    // ROADMAP follow-up: in-game Achievements hotspot, mirroring the Rules
    // hotspot above -- same y band (verified clear of this class's own
    // DECREMENT/VALUE/INCREMENT/BET row at y=[585,619)), sitting to its left
    // with a visible gap on both sides. Left edge (600) was measured, not
    // eyeballed: with this class's centered prompt text N/A here (BetStepper
    // renders no centered banner in this band, only its own controls at
    // y=585-619) the binding constraint is the *other* two classes sharing
    // this exact geometry (IllegalPlayFeedback/NextTrickPrompt), whose
    // longest centered message ends at x=567 (measured via headless
    // FontMetrics, bold default font) -- 600 leaves a 33px gap from that, and
    // 730 leaves a 30px gap before RULES_LEFT (760). "Achievements" (12
    // chars) is much wider than "Rules" (5): this box is sized to 130px
    // (vs. Rules' 60px) so the label has the same kind of padding headroom
    // Rules gets, not a tight fit -- see TestBetStepper's non-collision test
    // for the geometry proof.
    private static final String ACHIEVEMENTS_LABEL = "Achievements";
    private static final int ACHIEVEMENTS_TOP = 265, ACHIEVEMENTS_BOTTOM = 295;
    private static final int ACHIEVEMENTS_LEFT = 600, ACHIEVEMENTS_RIGHT = 730;

    private final int maxBet;
    private int value;

    public BetStepper(int maxBet) {
        this.maxBet = maxBet;
        this.value = 0;
    }

    public int getValue() {
        return value;
    }

    public void decrement() {
        if (value > 0) {
            value--;
        }
    }

    public void increment() {
        if (value < maxBet) {
            value++;
        }
    }

    /**
     * Returns the control at pixel (px, py), or null if the point hits no
     * control (a gap between controls, the non-clickable value display, or
     * outside the row entirely). Half-open rects, same convention as
     * Hand.cardAt: px >= left && px < right && py >= top && py < bottom.
     */
    public Control controlAt(int px, int py) {
        if (py < TOP || py >= BOTTOM) {
            return null;
        }
        if (px >= DECREMENT_LEFT && px < DECREMENT_RIGHT) {
            return Control.DECREMENT;
        }
        if (px >= INCREMENT_LEFT && px < INCREMENT_RIGHT) {
            return Control.INCREMENT;
        }
        if (px >= BET_LEFT && px < BET_RIGHT) {
            return Control.BET;
        }
        return null;
    }

    /**
     * Half-open rect hit-test for the Rules hotspot, same convention as
     * controlAt/NextTrickPrompt.isRulesHotspot. Kept separate from
     * controlAt/Control since it isn't one of this class's own bet-input
     * controls.
     */
    public boolean isRulesHotspot(int px, int py) {
        return px >= RULES_LEFT && px < RULES_RIGHT && py >= RULES_TOP && py < RULES_BOTTOM;
    }

    /**
     * Half-open rect hit-test for the Achievements hotspot, same convention
     * as isRulesHotspot. See the ACHIEVEMENTS_* fields' comment for how this
     * geometry was chosen clear of the Rules box and this class's own
     * control row.
     */
    public boolean isAchievementsHotspot(int px, int py) {
        return px >= ACHIEVEMENTS_LEFT && px < ACHIEVEMENTS_RIGHT && py >= ACHIEVEMENTS_TOP && py < ACHIEVEMENTS_BOTTOM;
    }

    @Override
    public void tick() {

    }

    @Override
    public void render(Graphics g) {
        g.setColor(Color.BLACK);
        g.drawRect(DECREMENT_LEFT, TOP, DECREMENT_RIGHT - DECREMENT_LEFT - 1, BOTTOM - TOP - 1);
        g.drawString("-", DECREMENT_LEFT + 10, BOTTOM - 10);

        g.drawRect(VALUE_LEFT, TOP, VALUE_RIGHT - VALUE_LEFT - 1, BOTTOM - TOP - 1);
        g.drawString(String.valueOf(value), VALUE_LEFT + 10, BOTTOM - 10);

        g.drawRect(INCREMENT_LEFT, TOP, INCREMENT_RIGHT - INCREMENT_LEFT - 1, BOTTOM - TOP - 1);
        g.drawString("+", INCREMENT_LEFT + 10, BOTTOM - 10);

        g.drawRect(BET_LEFT, TOP, BET_RIGHT - BET_LEFT - 1, BOTTOM - TOP - 1);
        g.drawString("Bet", BET_LEFT + 10, BOTTOM - 10);

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
}
