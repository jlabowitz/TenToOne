import java.util.ArrayList;
import java.util.List;

public class Round {
    private final int numCards;
    private final List<Player> players;
    private int currentPlayer;
    private final Card trumpCard;
    private final Suit trump;
    private final Deck deck;
    private boolean trumpBroken;
    /**
     * design/persistent-game-state.md Phase 4: promoted from playRound()'s
     * local variable so a checkpoint fired while a player is blocked mid-trick
     * can read the trick currently in progress. Set at the top of each loop
     * iteration, cleared once that trick resolves -- null between tricks.
     */
    private Trick currentTrick;

    private final int WIDTH, HEIGHT;
    private final Handler handler;

    public Round(int numCards, List<Player> players, int currentPlayer, int width, int height, Handler handler) {
        this.numCards = numCards;
        this.players = players;
        this.currentPlayer = currentPlayer;
        WIDTH = width;
        HEIGHT = height;
        this.handler = handler;

        //deal
        deck = new Deck();
        deal();

        //set trump suit
        this.trumpCard = deck.draw();
        this.trump = trumpCard.getSuit();
        System.out.println("The trump suit is " + trump);

        trumpBroken = false;

        //The round's starting player leads its first trick; from here on,
        //playRound()'s winner.wonTrick() call site moves the flag trick by
        //trick. currentPlayer/roundStartingPlayer is already the right
        //value to seed this with, so no separate lookup is needed.
        initializeTrickLeader();
    }

    /**
     * design/persistent-game-state.md Phase 5: reconstruction constructor --
     * accepts already-built state (trump card/suit, trumpBroken, and each
     * player already carrying its restored hand/bet/trickScore) instead of
     * dealing fresh and drawing a trump card from a new Deck -- the normal
     * constructor's deck/deal()/trump-draw are all skipped here, matching
     * design doc §1's "the undealt deck is dead once a round starts" (nothing
     * downstream of this constructor ever reads `deck`, so it's left null).
     *
     * `currentPlayer` is the seat leading the round's current (if mid-trick)
     * or next (if between tricks) trick -- this is Round's own currentPlayer
     * field's existing double duty (see playRound()'s doc), not a new
     * concept. A deliberate addition beyond design doc §2's literal
     * RoundSnapshot listing (that section doesn't call this field out
     * explicitly) -- see GameStateCodec's class doc for why it's needed:
     * without it, a round resumed between tricks has no way to know which
     * seat leads the next one.
     *
     * Callers reconstructing a round that's mid-trick must also call
     * setCurrentTrick(...) right after construction, once the corresponding
     * Trick has been built (see Trick's own reconstruction constructor);
     * between-tricks callers leave currentTrick null (this constructor's
     * default). Renders the trump card/player hand on screen the same way
     * the normal constructor's bet() call eventually would -- reconstruction
     * skips bet() entirely (betting already happened), so this constructor
     * does that rendering itself via renderTrumpCard()/renderPlayerHand().
     */
    public Round(int numCards, List<Player> players, int currentPlayer, int width, int height, Handler handler,
                 Card trumpCard, Suit trump, boolean trumpBroken) {
        this.numCards = numCards;
        this.players = players;
        this.currentPlayer = currentPlayer;
        WIDTH = width;
        HEIGHT = height;
        this.handler = handler;
        this.deck = null;
        this.trumpCard = trumpCard;
        this.trump = trump;
        this.trumpBroken = trumpBroken;

        positionHumanHand();
        renderTrumpCard();
        renderPlayerHand();
        initializeTrickLeader();
    }

    /**
     * design/persistent-game-state.md Phase 8: re-positions the human's hand
     * using the player's *current* on-screen x/y -- needed when a Round is
     * reconstructed from a snapshot before Game.renderPlayers() has actually
     * positioned the player objects (GameStateCodec.fromSnapshot builds
     * players and this Round together in one call, but Game's snapshot
     * constructor only calls renderPlayers() afterward, once it has the
     * reconstructed player list in hand). A no-op if there's no human
     * player. Public since the caller here is Game, not this class itself.
     */
    public void repositionHumanHand() {
        positionHumanHand();
    }

    /**
     * design/persistent-game-state.md Phase 5: sets the trick currently in
     * progress on a reconstructed Round -- see the reconstruction
     * constructor's doc. Package-private: only GameStateCodec (reconstruction)
     * calls this; the normal playRound() loop manages currentTrick itself.
     */
    void setCurrentTrick(Trick currentTrick) {
        this.currentTrick = currentTrick;
    }

    /** design/persistent-game-state.md Phase 4: the trick currently in progress, or null between tricks. */
    public Trick getCurrentTrick() {
        return currentTrick;
    }

    public Card getTrumpCard() {
        return trumpCard;
    }

    public Suit getTrump() {
        return trump;
    }

    public boolean getTrumpBroken() {
        return trumpBroken;
    }

    /** The seat leading the round's current (mid-trick) or next (between tricks) trick -- see the reconstruction constructor's doc. */
    public int getCurrentPlayer() {
        return currentPlayer;
    }

    private void initializeTrickLeader() {
        for (Player player : players) {
            player.setTrickLeader(false);
        }
        getPlayer(currentPlayer).setTrickLeader(true);
    }

    /**
     * ROADMAP item 1 (design/ai-and-polish.md §3/§4/§5): assembles the
     * BettingContext every bettor needs, once per player.bet() call --
     * priorBets accumulated as the loop goes (a fresh immutable snapshot
     * each iteration, not including the current bettor's own eventual bet),
     * isFirstBettor/isLastBettor from the loop index (i == 0 is the round's
     * starting player, who also leads the round's first trick; i ==
     * numPlayers()-1 is the last player.bet() call this loop makes), and the
     * settings toggle. numCardsThisRound isn't threaded separately: every
     * player's hand size equals numCards for the whole round (see deal()),
     * so each bettor already has the equivalent value via
     * getHand().getNumCards().
     */
    public void bet(int currentPlayer, GameSettings gameSettings) {
        renderTrumpCard();
        renderPlayerHand();
        for (Player player : players) {
            player.resetBet();
        }
        List<Integer> priorBets = new ArrayList<>();
        for (int i = 0; i < numPlayers(); i++) {
            boolean isFirstBettor = (i == 0);
            boolean isLastBettor = (i == numPlayers() - 1);
            // choose a bet
            Player player = getPlayer(currentPlayer);
            BettingContext context = new BettingContext(trump, List.copyOf(priorBets), numPlayers(),
                    isFirstBettor, isLastBettor, gameSettings.totalBetsCannotEqualTricks);
            player.bet(context);
            priorBets.add(player.getBet());
            currentPlayer = nextPlayer(currentPlayer);
        }
    }

    public void playRound() {
        trumpBroken = false;
        //for each trick
        for (int i = 0; i < numCards; i++) {
            //for each player
            int trickStartPlayer = currentPlayer;

            currentTrick = new Trick(players, currentPlayer, trump, trumpBroken, WIDTH, HEIGHT, handler);
            List<Card> cardsPlayed = currentTrick.play();
            if (!trumpBroken) {
                trumpBroken = currentTrick.getTrumpBroken();
            }
            currentTrick = null;

            //determine winner of trick
            int winnerIndex = determineTrickWinner(cardsPlayed, trump);
            int actualWinner = convertWinnerIndex(winnerIndex, trickStartPlayer);

            Player winner = getPlayer(actualWinner);
            getPlayer(trickStartPlayer).setTrickLeader(false);
            winner.setTrickLeader(true);
            winner.wonTrick();
            System.out.println(winner.getName() + " won the trick.");

            handler.removeAll(cardsPlayed);
            //led-suit HUD must already read "no suit led" in the gap between
            //this trick resolving and the next one starting
            for (Player player : players) {
                player.setLeadingSuit(null);
            }

            //winner of trick starts next round;
            currentPlayer = actualWinner;
        }
    }

    private void deal() {
        initializeHands();

        for (int i = 0; i < numCards; i++) {
            for (Player player : players) {
                Card card = deck.draw();
                //Should not be using getter method to add card
                player.getHand().addCard(card);
            }
        }
    }

    private void initializeHands() {
        for (Player player : players) {
            String name = player.getName();
            assert player.getHand() == null || player.getHand().getNumCards() == 0 : String.format("Player '%s' hand is not empty", name);
        }

        for (Player player : players) {
            if (player.getHand() == null) {
                player.setHand(new Hand(WIDTH, HEIGHT, player.getID()));
            }
        }
        positionHumanHand();
    }

    /**
     * Position the human's hand here, on the game-logic thread, so cardAt
     * hit-tests the on-screen position before any click is awaited.
     * Game.renderPlayers has already placed the human player at
     * (WIDTH, HEIGHT - 150) on this thread; Player.render's hand.setY(getY())
     * on the render thread then re-writes the same value, which is benign.
     * Factored out of initializeHands() (design/persistent-game-state.md
     * Phase 5) so the reconstruction constructor -- which skips deal()/
     * initializeHands() entirely, since hands are already built and restored
     * by the caller -- can still position the human's hand correctly.
     */
    private void positionHumanHand() {
        for (Player player : players) {
            if (player.getID() == ID.HUMAN) {
                Hand hand = player.getHand();
                hand.setX(player.getX());
                hand.setY(player.getY());
            }
        }
    }

    private void renderTrumpCard() {
        trumpCard.setX(50);
        trumpCard.setY(HEIGHT / 2);
        trumpCard.setTrump();
        handler.addObject(trumpCard);
    }

    private void renderPlayerHand() {
        Hand playersHand = getPlayer(0).getHand();
        handler.addObject(playersHand);
    }

    static int determineTrickWinner(List<Card> cardsPlayed, Suit trump) {
        int winner = 0;
        Card winningCard = cardsPlayed.get(winner);
        for (int i = 1; i < cardsPlayed.size(); i++) {
            Card card = cardsPlayed.get(i);
            if (isHigher(winningCard, card, trump)) {
                winner = i;
                winningCard = card;
            }
        }
        return winner;
    }

    /**
     * ROADMAP item 1 (design/ai-and-polish.md §3): "total bets cannot equal
     * number of tricks" -- the shared bet-legality rule every bettor (human
     * or AI) must satisfy, not just Human's own range check (subsumed here
     * as the bet<0/bet>maxBet branch below). Lives on Round (not Human)
     * since Round already owns numCards and orchestrates the betting loop --
     * a rule shared by every bettor shouldn't be namespaced under a
     * Human-specific method.
     *
     * Only the round's *last* bettor is constrained: everyone before them
     * bets without knowing the eventual total, so only the last bettor could
     * ever make the total land exactly on numCardsThisRound. forbiddenBet
     * falling outside [0, maxBet] is a natural no-op (every remaining legal
     * bet stays legal) -- no special-casing needed, since a bet that already
     * failed the range check can never equal an out-of-range forbiddenBet.
     */
    static boolean isLegalBet(int bet, int maxBet, int sumOfPriorBets,
                               int numCardsThisRound, boolean isLastBettor,
                               boolean totalBetsCannotEqualTricks) {
        if (bet < 0 || bet > maxBet) {
            return false;
        }
        if (!totalBetsCannotEqualTricks || !isLastBettor) {
            return true;
        }
        int forbiddenBet = numCardsThisRound - sumOfPriorBets;
        return bet != forbiddenBet;
    }

    /// Returns true if CARD is higher values than WINNINGCARD
    static boolean isHigher(Card winningCard, Card card, Suit trump) {
        Suit suit = card.getSuit();
        if (suit != winningCard.getSuit()) {
            return suit == trump;
        } else {
            return card.getValue().compareTo(winningCard.getValue()) > 0;
        }
    }

    private int convertWinnerIndex(int winnerIndex, int trickStartPlayer) {
        return (winnerIndex + trickStartPlayer) % numPlayers();
    }

    //Improve
    private Player getPlayer(int i) {
        assert 0 <= i && i < players.size() : i + " is not a valid player";
        //add other boundary as well
        return players.get(i);
    }

    private int numPlayers() {
        return players.size();
    }

    private int nextPlayer(int curr) {
        return (curr + 1) % numPlayers();
    }
}
