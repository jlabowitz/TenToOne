import java.awt.*;

/**
 * ROADMAP item 1: the rules/instructions view, reachable both pre-launch
 * (StartScreen's Rules button) and mid-game (NextTrickPrompt's Rules
 * hotspot, wired into Human.nextTrick() by the backend pass) -- identical
 * content and layout in both contexts by design, so a future in-game legend
 * (ROADMAP item 7, blocked on this item) can slot into LEGEND_TOP/BOTTOM
 * below without restructuring this view again.
 *
 * A full 840x630 canvas GameObject. Does NOT extend ModalOverlay -- that
 * class's panel is fixed at 640x430, too small for this page's content --
 * but reuses ModalOverlay.GOLD/TITLE_FONT/HEADER_FONT (protected, but
 * package-accessible since this codebase has no package declarations, so
 * same-package access applies without needing to extend it) and replicates
 * its scrim/panel/black-border visual language with its own larger geometry.
 *
 * Unlike RoundSummaryPanel's "click anywhere to continue," this requires an
 * explicit Back button -- a reference page meant to be read deliberately,
 * not a transient toast.
 *
 * All rules content below was checked against actual game logic
 * (Player.legalCards, Round.isHigher/determineTrickWinner,
 * Game.adjustScores/numCardsThisRound) as of this pass -- see the
 * completion report for specifics.
 */
public class RulesView extends GameObject {
    private static final int PANEL_X = 40, PANEL_Y = 20, PANEL_W = 760, PANEL_H = 590;
    public static final int CONTENT_LEFT = PANEL_X + 30, CONTENT_RIGHT = PANEL_X + PANEL_W - 30;

    private static final int TITLE_Y = 65;

    private static final int OBJECTIVE_HEADER_Y = 100;
    private static final int OBJECTIVE_BODY_Y = 120;

    private static final int DEAL_HEADER_Y = 150;
    private static final int DEAL_BODY_1_Y = 170;
    private static final int DEAL_BODY_2_Y = 188;

    private static final int BETTING_HEADER_Y = 218;
    private static final int BETTING_BODY_1_Y = 238;
    private static final int BETTING_BODY_2_Y = 256;
    private static final int BETTING_BODY_3_Y = 274;

    private static final int TRICK_HEADER_Y = 304;
    private static final int TRICK_BODY_1_Y = 324;
    private static final int TRICK_BODY_2_Y = 342;
    private static final int TRICK_BODY_3_Y = 360;
    private static final int TRICK_BODY_4_Y = 378;

    private static final int INDICATORS_HEADER_Y = 408;
    private static final int INDICATORS_BODY_1_Y = 428;
    private static final int INDICATORS_BODY_2_Y = 446;
    private static final int INDICATORS_BODY_3_Y = 464;

    private static final int DIVIDER_Y = 480;

    /** Reserved for ROADMAP item 7 (in-game legend); ships empty in this pass -- no placeholder text/border, see class doc. */
    public static final int LEGEND_TOP = 490, LEGEND_BOTTOM = 570;

    private static final int BACK_TOP = 576, BACK_BOTTOM = 602;
    private static final int BACK_LEFT = 680, BACK_RIGHT = 760;

    /**
     * Same lifecycle shape as BetStepper/NextTrickPrompt: add to the
     * Handler, block on clicks until the Back button is hit, remove in a
     * finally. Called from two places by the backend pass (pre-launch from
     * Game, mid-game from Human.nextTrick()) -- signature intentionally
     * fixed, don't change it independently of those callers.
     */
    public static void showBlocking(Handler handler, MouseInput mouseInput) {
        RulesView view = new RulesView();
        handler.addObject(view);
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (view.isBackButton(click.x, click.y)) {
                    return;
                }
            }
        } finally {
            handler.removeObject(view);
        }
    }

    /** Half-open rect hit-test, same convention as BetStepper.controlAt. */
    public boolean isBackButton(int px, int py) {
        return px >= BACK_LEFT && px < BACK_RIGHT && py >= BACK_TOP && py < BACK_BOTTOM;
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
        drawCentered(g, "How to Play", TITLE_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("Objective", CONTENT_LEFT, OBJECTIVE_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("Score the most points across 10 rounds by winning tricks.", CONTENT_LEFT, OBJECTIVE_BODY_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("The Deal", CONTENT_LEFT, DEAL_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("Round 1 deals 10 cards to each player; each round deals one fewer.", CONTENT_LEFT, DEAL_BODY_1_Y);
        g.drawString("A trump suit is revealed right after the deal each round.", CONTENT_LEFT, DEAL_BODY_2_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("Betting", CONTENT_LEFT, BETTING_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("Before play, bet how many tricks you'll win this round (0 to your hand size).", CONTENT_LEFT, BETTING_BODY_1_Y);
        g.drawString("Guess exactly right: score tricks won, plus a 10-point bonus.", CONTENT_LEFT, BETTING_BODY_2_Y);
        g.drawString("Miss your bet: score only the tricks you actually won.", CONTENT_LEFT, BETTING_BODY_3_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("Playing a Trick", CONTENT_LEFT, TRICK_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("Follow the suit that was led if you're able to.", CONTENT_LEFT, TRICK_BODY_1_Y);
        g.drawString("Can't follow suit? Play any card, including trump.", CONTENT_LEFT, TRICK_BODY_2_Y);
        g.drawString("The highest trump played wins the trick; with no trump played, the highest card of the led suit wins.", CONTENT_LEFT, TRICK_BODY_3_Y);
        g.drawString("You can't lead trump until it's been \"broken\" earlier in the round -- unless your whole hand is trump.", CONTENT_LEFT, TRICK_BODY_4_Y);

        g.setFont(ModalOverlay.HEADER_FONT);
        g.drawString("On-Screen Indicators", CONTENT_LEFT, INDICATORS_HEADER_Y);
        g.setFont(defaultFont);
        g.drawString("A black dot beside a name marks that trick's current leader.", CONTENT_LEFT, INDICATORS_BODY_1_Y);
        g.drawString("The \"Led: [suit]\" line shows the suit led in the trick underway.", CONTENT_LEFT, INDICATORS_BODY_2_Y);
        g.drawString("A gold ring marks the highest card played so far in the trick underway.", CONTENT_LEFT, INDICATORS_BODY_3_Y);

        g.setColor(Color.BLACK);
        g.drawLine(CONTENT_LEFT, DIVIDER_Y, CONTENT_RIGHT, DIVIDER_Y);

        //LEGEND_TOP..LEGEND_BOTTOM intentionally left blank -- see class doc
        //and ROADMAP item 7.

        g.setColor(Color.BLACK);
        g.drawRect(BACK_LEFT, BACK_TOP, BACK_RIGHT - BACK_LEFT - 1, BACK_BOTTOM - BACK_TOP - 1);
        drawCenteredIn(g, "Back", BACK_LEFT, BACK_RIGHT, BACK_BOTTOM - 8);

        g.setFont(defaultFont);
    }

    /** Draws TEXT horizontally centered within CONTENT_LEFT..CONTENT_RIGHT. */
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
