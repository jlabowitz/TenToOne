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
    }
}
