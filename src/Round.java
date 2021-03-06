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
    }

    public void bet(int currentPlayer) {
        renderTrumpCard();
        renderPlayerHand();
        for (int i = 0; i < numPlayers(); i++) {
            // choose a bet
            getPlayer(currentPlayer).bet(trump);
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
            winner.wonTrick();
            System.out.println(winner.getName() + " won the trick.");

            handler.removeAll(cardsPlayed);

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
