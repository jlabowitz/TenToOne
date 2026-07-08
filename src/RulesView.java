import java.awt.*;

/**
 * ROADMAP item 1: the rules/instructions view, reachable both pre-launch
 * (StartScreen's Rules button) and mid-game (NextTrickPrompt's Rules
 * hotspot, wired into Human.nextTrick() by the backend pass) -- identical
 * content and layout in both contexts by design, so a future in-game legend
 * (ROADMAP item 7, blocked on this item) can slot into LEGEND_TOP/BOTTOM
 * below without restructuring this view again. That item isn't built yet,
 * so LEGEND_TOP/BOTTOM ships empty in this pass -- no placeholder text or
 * border, just the reserved vertical space between the divider and the
 * Back button.
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
 *
 * ROADMAP item 10 follow-up: two changes --
 * (1) a click outside the panel now dismisses (with no other action),
 * mirroring AchievementsView/PauseView's own miss-click convention, via the
 * shared ModalDismiss helper (see that class's doc for why this is a static
 * helper rather than a shared base class).
 * (2) PANEL_H shrank (590 -> 548): LEGEND_TOP/BOTTOM's reserved-for-item-7
 * band shrank from 80px to 40px (still a real reservation, just no longer
 * sized as generously as an unbuilt feature's guessed footprint) and
 * BACK_TOP/BOTTOM moved up to follow it -- see LEGEND_TOP/BOTTOM's own doc.
 */
public class RulesView extends GameObject {
    private static final int PANEL_X = 40, PANEL_Y = 20, PANEL_W = 760, PANEL_H = 548;
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

    /**
     * Reserved for ROADMAP item 7 (in-game legend); see class doc for why it
     * ships empty in this pass. ROADMAP item 10 follow-up: shrank from
     * 490-570 (80px) to 490-530 (40px) as part of trimming this panel's
     * excess bottom dead space -- still a genuine reservation (item 7 isn't
     * built yet, so its real footprint is unknown), just no longer sized as
     * generously as an untested guess; a future item 7 pass is free to grow
     * this back if 40px turns out too tight for whatever it actually needs.
     */
    public static final int LEGEND_TOP = 490, LEGEND_BOTTOM = 530;

    private static final int BACK_TOP = 536, BACK_BOTTOM = 562;
    private static final int BACK_LEFT = 680, BACK_RIGHT = 760;

    /**
     * Same lifecycle shape as BetStepper/NextTrickPrompt: add to the
     * Handler, block on clicks until the Back button is hit, remove in a
     * finally. Called from several places (pre-launch from Game's Start
     * Screen loop, mid-game from Human's three loops) -- signature
     * intentionally fixed, don't change it independently of those callers.
     *
     * ACHIEVEMENTTOAST's click-to-dismiss hotspot is checked ahead of the
     * Back button, code-review Finding 1: this view is one of the things
     * AchievementToast's keepOnTop() renders on top of (see that class's
     * doc), so a toast showing while this view is up must stay dismissible
     * here too, not just from Human's mid-game loops. This is a no-op
     * whenever the toast isn't actually showing (isToastHotspot() is gated
     * on isShowingSomething()) -- including the pre-activate() Start Screen
     * call site, where nothing is ever queued-and-visible yet.
     */
    public static void showBlocking(Handler handler, MouseInput mouseInput, AchievementToast achievementToast) {
        RulesView view = new RulesView();
        handler.addObject(view);
        InteractionLog.logShown("RulesView");
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (view.isBackButton(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "RulesView.Back");
                    return;
                }
                if (ModalDismiss.isOutsidePanel(click, view::isInsidePanel, "RulesView")) {
                    return;
                }
                InteractionLog.logClick(click.x, click.y, "no control matched (RulesView)");
            }
        } finally {
            handler.removeObject(view);
        }
    }

    /** Half-open rect hit-test, same convention as BetStepper.controlAt. */
    public boolean isBackButton(int px, int py) {
        return px >= BACK_LEFT && px < BACK_RIGHT && py >= BACK_TOP && py < BACK_BOTTOM;
    }

    /** Half-open rect hit-test for the panel itself, used to distinguish an inert click on the panel from a true miss-click (see ModalDismiss). */
    public boolean isInsidePanel(int px, int py) {
        return px >= PANEL_X && px < PANEL_X + PANEL_W && py >= PANEL_Y && py < PANEL_Y + PANEL_H;
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

        //LEGEND_TOP..LEGEND_BOTTOM intentionally left blank -- see class doc.

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
