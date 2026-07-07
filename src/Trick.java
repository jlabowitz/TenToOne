import java.util.ArrayList;
import java.util.List;

public class Trick {
    private final List<Player> players;
    private final int trickStartPlayer;
    private int currentPlayer;
    private final Suit trump;
    private boolean trumpBroken;
    /**
     * Design/persistent-game-state.md Phase 4: promoted from play()'s local
     * variable so a checkpoint fired mid-trick (from inside a blocked
     * Human.playCard()) can read the trick's leading suit before play()
     * returns. Null until the trick's first card lands.
     */
    private Suit leading;
    /**
     * Cards played so far this trick, and which seat (index into `players`)
     * played each one, in play order -- design/persistent-game-state.md
     * Phase 4/§1. Populated as play() proceeds; used both by the live game
     * (getCardsPlayed()) and by the reconstruction constructor below to
     * resume a trick partway through.
     */
    private final List<SeatCardPlay> cardsPlayedBySeat = new ArrayList<>();

    private final int WIDTH, HEIGHT;
    private final Handler handler;
    /** Set only by the reconstruction constructor -- see play()'s resume-from-partway doc. */
    private final int resumeFromIteration;


    public Trick(List<Player> players, int currentPlayer, Suit trump, boolean trumpBroken, int width, int height, Handler handler) {
        this.players = players;
        this.trickStartPlayer = currentPlayer;
        this.currentPlayer = currentPlayer;
        this.trump = trump;
        this.trumpBroken = trumpBroken;
        this.WIDTH = width;
        this.HEIGHT = height;
        this.handler = handler;
        this.resumeFromIteration = 0;
    }

    /**
     * design/persistent-game-state.md Phase 5: reconstruction constructor --
     * pre-seeds cardsPlayedBySeat/leading/currentPlayer from a TrickSnapshot
     * instead of starting a fresh trick, and makes play() resume from
     * wherever the snapshot left off instead of starting at i=0. Cards in
     * `alreadyPlayed` must already have been removed from each player's Hand
     * by the caller (GameStateCodec.fromSnapshot) via Hand reconstruction --
     * this constructor does not touch any Hand.
     */
    public Trick(List<Player> players, int trickStartPlayer, int currentPlayer, Suit trump, boolean trumpBroken,
                 Suit leading, List<SeatCardPlay> alreadyPlayed, int width, int height, Handler handler) {
        this.players = players;
        this.trickStartPlayer = trickStartPlayer;
        this.currentPlayer = currentPlayer;
        this.trump = trump;
        this.trumpBroken = trumpBroken;
        this.leading = leading;
        this.WIDTH = width;
        this.HEIGHT = height;
        this.handler = handler;
        this.cardsPlayedBySeat.addAll(alreadyPlayed);
        this.resumeFromIteration = alreadyPlayed.size();
        //Code-review fix: play()'s own loop calls player.setLeadingSuit(...)
        //on every player exactly once, the moment the trick's first card
        //lands (see the `if (leading == null)` branch below) -- but a
        //reconstructed mid-trick Trick already has `leading` non-null here,
        //so that branch never fires again for the rest of a resumed trick,
        //leaving every player's leadingSuit field (and thus the "Led: --"
        //HUD indicator) stuck at null/blank even though a suit really has
        //been led. Replay that same one-time assignment here whenever the
        //snapshot captured a trick whose first card had already landed.
        if (leading != null) {
            for (Player p : players) {
                p.setLeadingSuit(leading);
            }
        }
    }

    /** Trick.play()'s per-play seat index + card, in play order -- design/persistent-game-state.md Phase 4. */
    public List<SeatCardPlay> getCardsPlayedBySeat() {
        return cardsPlayedBySeat;
    }

    /** Whose turn to play next -- design/persistent-game-state.md Phase 4. */
    public int getCurrentPlayer() {
        return currentPlayer;
    }

    /** The suit led this trick, or null if no card has landed yet -- design/persistent-game-state.md Phase 4. */
    public Suit getLeading() {
        return leading;
    }

    /** The seat that led (or will lead) this trick. */
    public int getTrickStartPlayer() {
        return trickStartPlayer;
    }

    public List<Card> play() {
        List<Card> cardsPlayed = new ArrayList<>();
        Card runningHighCard = null;
        // design/persistent-game-state.md Phase 5: resume a reconstructed
        // trick from wherever the snapshot left off -- re-render the
        // already-played cards (same positioning/handler-registration a live
        // play would have done) and replay their bookkeeping (cardsPlayed
        // list, trumpBroken, running high card) without re-asking any player
        // to play them again, then fall through to the normal loop below
        // starting at resumeFromIteration instead of 0.
        for (SeatCardPlay seatCardPlay : cardsPlayedBySeat) {
            Card card = renderCardAt(seatCardPlay.seatIndex, seatCardPlay.card.suit, seatCardPlay.card.value);
            if (!trumpBroken && card.getSuit() == trump) {
                trumpBroken = true;
            }
            cardsPlayed.add(card);
            runningHighCard = updateRunningHighCard(cardsPlayed, runningHighCard);
        }
        for (int i = resumeFromIteration; i < numPlayers(); i++) {
            //play a card

            Player player = getPlayer(currentPlayer);
            Card card = player.playCard(cardsPlayed, leading, trump, trumpBroken);

            if (player.getID() == ID.AI) {
                card.setX(player.getX());
                // +30, not +20: leaves enough clearance below the AI's name
                // text for the high-card ring (drawn up to 14px above the
                // card) to not visually collide with it. Player.render's
                // trickScore/score lines were pushed down (+165/+185) to
                // give the ring room below the card too.
                card.setY(player.getY() + 30);
            } else {
                card.setX((WIDTH - 100) / 2);
                card.setY(HEIGHT - 300);
            }
            handler.addObject(card);

            if (!trumpBroken && card.getSuit() == trump) {
                trumpBroken = true;
            }
            cardsPlayed.add(card);
            cardsPlayedBySeat.add(new SeatCardPlay(currentPlayer, new CardSnapshot(card.getSuit(), card.getValue())));
            if (leading == null) {
                leading = cardsPlayed.get(cardsPlayed.size() - 1).getSuit();
                for (Player p : players) {
                    p.setLeadingSuit(leading);
                }
            }

            //recompute the running high card over cards played so far -- not
            //new game logic, just an earlier/more frequent call site for
            //Round.determineTrickWinner/isHigher, which already implements
            //this comparison for the trick's final card list
            runningHighCard = updateRunningHighCard(cardsPlayed, runningHighCard);

            currentPlayer = nextPlayer(currentPlayer);
        }
        getPlayer(0).nextTrick();
        return cardsPlayed;
    }

    /** Recomputes/re-flags the running high card over cardsPlayed so far, returning the new pointer. */
    private Card updateRunningHighCard(List<Card> cardsPlayed, Card runningHighCard) {
        int highIndex = Round.determineTrickWinner(cardsPlayed, trump);
        Card newHighCard = cardsPlayed.get(highIndex);
        if (newHighCard != runningHighCard) {
            if (runningHighCard != null) {
                runningHighCard.setHighCard(false);
            }
            newHighCard.setHighCard(true);
        }
        return newHighCard;
    }

    /**
     * design/persistent-game-state.md Phase 5: re-creates and re-registers
     * (with the handler, at the same on-screen position a live play would
     * have used) a Card for a seat's already-recorded play, on reconstruction
     * only -- mirrors play()'s own AI/human positioning branch exactly.
     */
    private Card renderCardAt(int seatIndex, Suit suit, CardValue value) {
        Player player = getPlayer(seatIndex);
        Card card = new Card(suit, value);
        if (player.getID() == ID.AI) {
            card.setX(player.getX());
            card.setY(player.getY() + 30);
        } else {
            card.setX((WIDTH - 100) / 2);
            card.setY(HEIGHT - 300);
        }
        handler.addObject(card);
        return card;
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

    public boolean getTrumpBroken() {
        return trumpBroken;
    }
}
