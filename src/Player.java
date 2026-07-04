import java.awt.*;
import java.util.List;

public abstract class Player extends GameObject{
    private String name;
    private Hand hand;
    private int bet;
    private boolean hasBet;
    private int score;
    private int trickScore;
    protected ID id;

    /**
     * Whether this player is the current trick's leader (who led/will lead
     * it -- not necessarily whose turn it currently is). Written by
     * Round.playRound() (game-logic thread) as tricks resolve, read every
     * frame by the render thread in render() below -- volatile per this
     * codebase's established cross-thread field pattern (see Game.running).
     */
    private volatile boolean trickLeader;

    /**
     * The suit led in the trick currently in progress, or null between
     * tricks/during betting. Written by Trick.play() (game-logic thread) the
     * instant the trick's first card lands, and reset by Round.playRound()
     * once the trick resolves -- read every frame by the render thread in
     * the HUMAN branch of render() below, so volatile for the same reason
     * as trickLeader above.
     */
    private volatile Suit leadingSuit;

    /*public Player() {
        this(null);
    }*/

    public Player(String name) {
        this.name = name;
        this.hand = null;
        this.score = 0;
        this.trickScore = 0;
        this.bet = 0;
        this.hasBet = false;
    }

    public String getName() {
        return name;
    }

    /**
     * ROADMAP item 1 (play-again restart): lets the restarted game apply a
     * freshly re-captured Start Screen name to the same Human instance
     * rather than constructing a new Player.
     */
    public void setName(String name) {
        this.name = name;
    }

    public Hand getHand() {
        return hand;
    }

    public void setHand(Hand hand) {
        this.hand = hand;
    }

    public int getBet() {
        return bet;
    }

    public void setBet(int bet) {
        this.bet = bet;
        this.hasBet = true;
    }

    public boolean hasBet() {
        return hasBet;
    }

    public void resetBet() {
        bet = 0;
        hasBet = false;
    }

    public abstract void bet(Suit trump);

    public int getScore() {
        return score;
    }

    public ID getID() {
        return id;
    }

    public void increaseScore(int n) {
        score += n;
    }

    public int getTrickScore() {
        return trickScore;
    }

    public void wonTrick() {
        trickScore++;
    }

    public void resetTrickScore() {
        trickScore = 0;
    }

    /**
     * ROADMAP item 1 (play-again restart): unlike trickScore (reset every
     * round via resetTrickScore()), nothing resets the running game score
     * today -- this is the dedicated reset for starting a genuinely new
     * game on the same Player instance.
     */
    public void resetScore() {
        score = 0;
    }

    /**
     * ROADMAP item 1 (play-again restart): bundles every per-game reset
     * needed before a restarted game's first round is dealt, mirroring this
     * class's existing small-dedicated-reset-method convention
     * (resetBet()/resetTrickScore()) -- this is the one call site that needs
     * all of them at once, the same bundling pattern
     * Round.initializeTrickLeader() already uses across all players for its
     * own purpose. hand is reset to null (not just left empty) so the new
     * game's first Round.initializeHands() builds a genuinely fresh Hand
     * rather than relying on "empty but not null" holding across a whole new
     * game.
     */
    public void resetForNewGame() {
        resetBet();
        resetTrickScore();
        resetScore();
        setTrickLeader(false);
        setLeadingSuit(null);
        setHand(null);
    }

    public boolean isTrickLeader() {
        return trickLeader;
    }

    public void setTrickLeader(boolean trickLeader) {
        this.trickLeader = trickLeader;
    }

    public Suit getLeadingSuit() {
        return leadingSuit;
    }

    public void setLeadingSuit(Suit leadingSuit) {
        this.leadingSuit = leadingSuit;
    }

    /** Diameter (px) of the trick-leader dot. */
    private static final int DOT_DIAMETER = 10;
    /** Horizontal gap (px) between the end of the name text and the dot. */
    private static final int DOT_GAP = 4;

    /**
     * Bounding box (for g.fillOval) of the trick-leader dot, placed
     * immediately to the right of the rendered name text -- not above/left
     * of it. An above/left placement (the original design) collided with
     * the human's "Led:" HUD line one row above, and risked clipping
     * off-screen for the leftmost AI seat (x=0); right-of-text avoids both,
     * since no player's name runs close enough to the window's right edge
     * to clip, and it never depends on what's drawn on the line above.
     * Uses the same FontMetrics/name-width measurement already used for the
     * "Led:" line's glyph placement, rather than a guessed fixed offset.
     */
    static Rectangle trickLeaderDotBounds(FontMetrics metrics, String name, int nameX, int nameY) {
        int textEndX = nameX + metrics.stringWidth(name);
        int centerX = textEndX + DOT_GAP + DOT_DIAMETER / 2;
        int centerY = nameY - metrics.getAscent() / 2;
        return new Rectangle(centerX - DOT_DIAMETER / 2, centerY - DOT_DIAMETER / 2, DOT_DIAMETER, DOT_DIAMETER);
    }

    public abstract Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken);

    public void nextTrick() {

    }

    //Method could be made static or put in another file
    public List<Card> legalCards(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        Hand hand = getHand();
        if (cardsPlayed.isEmpty()) {
            if (trumpBroken) {
                return hand.getCards();
            } else {
                List<Card> playable = hand.getCardsNotOfSuit(trump);
                if (!playable.isEmpty()) {
                    return playable;
                } else {
                    return hand.getCards();
                }
            }
        } else {
            if (hand.hasSuit(leading)) {
                return hand.getCardsOfSuit(leading);
            } else {
                return hand.getCards();
            }
        }
    }

    @Override
    public void tick() {

    }

    @Override
    public void render(Graphics g) {
        if (id == ID.AI) {
            g.setColor(Color.BLACK);
            g.drawString(getName(), getX(), getY());
            // +165/+185, not +150/+170: leaves room below for the AI's
            // played card and its high-card ring (see Trick.java) without
            // the ring's bottom edge touching this text.
            g.drawString(getTrickScore() + "/" + getBet(), getX(), getY() + 165);
            g.drawString("Score: " + getScore(), getX(), getY() + 185);
            renderTrickLeaderDot(g, getX(), getY(), getName());
        }
        else if (id == ID.HUMAN) {
            Hand hand = getHand();
            //hand is null until the first round has been dealt
            if (hand == null) {
                return;
            }
            hand.setY(getY());
            hand.render(g);

            renderLeadingSuit(g);
            g.setColor(Color.BLACK);
            g.drawString(getName(), HUD_X, HUD_NAME_Y);
            g.drawString(getTrickScore() + "/" + (hasBet() ? String.valueOf(getBet()) : "–"), HUD_X, HUD_BET_TRICKS_Y);
            g.drawString("Score: " + getScore(), HUD_X, HUD_SCORE_Y);
            renderTrickLeaderDot(g, HUD_X, HUD_NAME_Y, getName());
        }
    }

    /**
     * Human HUD block placement -- moved (per user request) from next to the
     * player's own hand to sit beside the trump card instead, since that's
     * the other place on screen this information is conceptually related to.
     * The trump card itself is positioned in Round.renderTrumpCard() at
     * x=50, y=HEIGHT/2; these constants pick up just past its right edge
     * (card width 60 + 10px trump-border padding on each side = 130, plus a
     * visible gap), with the 4 HUD lines vertically centered on the card's
     * own height (100 + 2*10 border = 120), using the same 18px line rhythm
     * as before.
     */
    private static final int HUD_X = 140;
    private static final int HUD_LED_SUIT_Y = 340;
    private static final int HUD_NAME_Y = 358;
    private static final int HUD_BET_TRICKS_Y = 376;
    private static final int HUD_SCORE_Y = 394;

    private void renderTrickLeaderDot(Graphics g, int nameX, int nameY, String name) {
        if (!isTrickLeader()) {
            return;
        }
        Rectangle bounds = trickLeaderDotBounds(g.getFontMetrics(), name, nameX, nameY);
        g.setColor(Color.BLACK);
        g.fillOval(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /** Topmost HUD line, alongside the trump card -- see HUD_X/HUD_LED_SUIT_Y above. */
    private void renderLeadingSuit(Graphics g) {
        Suit leadingSuit = getLeadingSuit();
        g.setColor(Color.BLACK);
        if (leadingSuit == null) {
            g.drawString("Led: –", HUD_X, HUD_LED_SUIT_Y);
            return;
        }
        String prefix = "Led: ";
        g.drawString(prefix, HUD_X, HUD_LED_SUIT_Y);
        FontMetrics metrics = g.getFontMetrics();
        int glyphX = HUD_X + metrics.stringWidth(prefix);
        String glyph = leadingSuit.getGlyph() + " ";
        g.setColor(leadingSuit.getColor());
        g.drawString(glyph, glyphX, HUD_LED_SUIT_Y);

        g.setColor(Color.BLACK);
        int nameX = glyphX + metrics.stringWidth(glyph);
        g.drawString(leadingSuit.getDisplayName(), nameX, HUD_LED_SUIT_Y);
    }
}
