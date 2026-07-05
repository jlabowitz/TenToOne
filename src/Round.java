import java.util.List;

public class Round {
    private final int numCards;
    private final List<Player> players;
    private int currentPlayer;
    private final Card trumpCard;
    private final Suit trump;
    private final Deck deck;
    private boolean trumpBroken;

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

    private void initializeTrickLeader() {
        for (Player player : players) {
            player.setTrickLeader(false);
        }
        getPlayer(currentPlayer).setTrickLeader(true);
    }

    /**
     * ROADMAP item 1 (design/ai-and-polish.md §3): threads the context every
     * bettor needs to self-check Round.isLegalBet -- sumOfPriorBets
     * accumulated as the loop goes, isLastBettor from the loop index (the
     * player right before the round's starting player in turn order, i.e.
     * the last player.bet() call this loop makes), and the settings toggle.
     * numCardsThisRound isn't threaded separately: every player's hand size
     * equals numCards for the whole round (see deal()), so each bettor
     * already has the equivalent value via getHand().getNumCards().
     */
    public void bet(int currentPlayer, GameSettings gameSettings) {
        renderTrumpCard();
        renderPlayerHand();
        for (Player player : players) {
            player.resetBet();
        }
        int sumOfPriorBets = 0;
        for (int i = 0; i < numPlayers(); i++) {
            boolean isLastBettor = (i == numPlayers() - 1);
            // choose a bet
            Player player = getPlayer(currentPlayer);
            player.bet(trump, sumOfPriorBets, isLastBettor, gameSettings.totalBetsCannotEqualTricks);
            sumOfPriorBets += player.getBet();
            currentPlayer = nextPlayer(currentPlayer);
        }
    }

    public void playRound() {
        trumpBroken = false;
        //for each trick
        for (int i = 0; i < numCards; i++) {
            //for each player
            int trickStartPlayer = currentPlayer;

            Trick trick = new Trick(players, currentPlayer, trump, trumpBroken, WIDTH, HEIGHT, handler);
            List<Card> cardsPlayed = trick.play();
            if (!trumpBroken) {
                trumpBroken = trick.getTrumpBroken();
            }

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
            if (player.getID() == ID.HUMAN) {
                //Position the hand here, on the game-logic thread, so cardAt
                //hit-tests the on-screen position before any click is awaited.
                //Game.renderPlayers has already placed the human player at
                //(WIDTH, HEIGHT - 150) on this thread; Player.render's
                //hand.setY(getY()) on the render thread then re-writes the
                //same value, which is benign.
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
