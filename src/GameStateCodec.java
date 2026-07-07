import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * design/persistent-game-state.md §3: two directions between the live
 * Game/Round/Trick/Player object graph and the plain-data GameStateSnapshot
 * tree.
 *
 * toSnapshot reads live state; it never fails (every input is already a
 * consistent live object graph).
 *
 * fromSnapshot is NOT a replay -- it directly reconstructs Player/Hand/Round/
 * Trick-shaped state from the snapshot's already-captured hands/bets/cards,
 * skipping Round's normal deal()/trump-draw and Trick's normal from-i=0 loop
 * via the reconstruction constructors added to those classes (Phase 4/5).
 * Whole-load failure (an unknown archetypeId) throws
 * GameStateReconstructionException -- callers must fall back to "no saved
 * game" rather than attempt a partial reconstruction, mirroring
 * SaveStore.parse()'s convention.
 */
public class GameStateCodec {

    /** Bundles fromSnapshot's reconstructed live state -- players plus the round in progress, if any (null between rounds). */
    public static class Reconstructed {
        public final List<Player> players;
        public final Round round;

        public Reconstructed(List<Player> players, Round round) {
            this.players = players;
            this.round = round;
        }
    }

    private GameStateCodec() {
    }

    // --- toSnapshot ---

    public static GameStateSnapshot toSnapshot(int roundIndex, int roundStartingPlayer, int roundsHitBonusThisGame,
                                                boolean wasSoleLastAtHalfway, List<Player> players, Round currentRound) {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        snapshot.savedAt = Instant.now();
        snapshot.roundIndex = roundIndex;
        snapshot.roundStartingPlayer = roundStartingPlayer;
        snapshot.roundsHitBonusThisGame = roundsHitBonusThisGame;
        snapshot.wasSoleLastAtHalfway = wasSoleLastAtHalfway;
        for (Player player : players) {
            snapshot.players.add(toPlayerSnapshot(player));
        }
        if (currentRound != null) {
            snapshot.round = toRoundSnapshot(currentRound);
        }
        return snapshot;
    }

    private static PlayerSnapshot toPlayerSnapshot(Player player) {
        PlayerSnapshot ps = new PlayerSnapshot();
        ps.name = player.getName();
        ps.archetypeId = player.archetypeId();
        ps.score = player.getScore();
        ps.bet = player.getBet();
        ps.hasBet = player.hasBet();
        ps.trickScore = player.getTrickScore();
        Hand hand = player.getHand();
        if (hand != null) {
            for (Card card : hand.getCards()) {
                ps.hand.add(new CardSnapshot(card.getSuit(), card.getValue()));
            }
        }
        return ps;
    }

    private static RoundSnapshot toRoundSnapshot(Round round) {
        RoundSnapshot rs = new RoundSnapshot();
        Card trumpCard = round.getTrumpCard();
        rs.trumpCard = new CardSnapshot(trumpCard.getSuit(), trumpCard.getValue());
        rs.trumpBroken = round.getTrumpBroken();
        rs.currentPlayer = round.getCurrentPlayer();
        Trick currentTrick = round.getCurrentTrick();
        if (currentTrick != null) {
            rs.currentTrick = toTrickSnapshot(currentTrick);
        }
        return rs;
    }

    private static TrickSnapshot toTrickSnapshot(Trick trick) {
        TrickSnapshot ts = new TrickSnapshot();
        ts.trickStartPlayer = trick.getTrickStartPlayer();
        ts.currentPlayer = trick.getCurrentPlayer();
        ts.leadingSuit = trick.getLeading();
        for (SeatCardPlay play : trick.getCardsPlayedBySeat()) {
            ts.cardsPlayedBySeat.add(new SeatCardPlay(play.seatIndex,
                    new CardSnapshot(play.card.suit, play.card.value)));
        }
        return ts;
    }

    // --- fromSnapshot ---

    /**
     * width/height/handler are the same live collaborators Round/Trick
     * already need; mouseInput/achievementToast/saveData/checkpointSaver are
     * only used for reconstructing the human seat (see PlayerArchetypeRegistry's
     * own doc for why non-human seats don't need them).
     */
    public static Reconstructed fromSnapshot(GameStateSnapshot snapshot, int width, int height, Handler handler,
                                              MouseInput mouseInput, AchievementToast achievementToast,
                                              SaveData saveData, Runnable checkpointSaver)
            throws GameStateReconstructionException {
        if (snapshot == null) {
            throw new GameStateReconstructionException("snapshot is null");
        }
        List<Player> players = new ArrayList<>();
        for (PlayerSnapshot ps : snapshot.players) {
            players.add(toPlayer(ps, width, height, handler, mouseInput, achievementToast, saveData, checkpointSaver));
        }
        Round round = null;
        if (snapshot.round != null) {
            round = toRound(snapshot, players, width, height, handler);
        }
        return new Reconstructed(players, round);
    }

    private static Player toPlayer(PlayerSnapshot ps, int width, int height, Handler handler, MouseInput mouseInput,
                                    AchievementToast achievementToast, SaveData saveData, Runnable checkpointSaver)
            throws GameStateReconstructionException {
        Player player;
        if (Human.ARCHETYPE_ID.equals(ps.archetypeId)) {
            player = new Human(ps.name, mouseInput, handler, achievementToast, saveData, checkpointSaver);
        } else {
            Function<String, Player> factory = PlayerArchetypeRegistry.lookup(ps.archetypeId);
            if (factory == null) {
                throw new GameStateReconstructionException("Unknown archetypeId: " + ps.archetypeId);
            }
            player = factory.apply(ps.name);
        }

        Hand hand = new Hand(width, height, player.getID());
        for (CardSnapshot cs : ps.hand) {
            hand.addCard(new Card(cs.suit, cs.value));
        }
        player.setHand(hand);

        if (ps.hasBet) {
            player.setBet(ps.bet);
        } else {
            player.resetBet();
        }
        for (int i = 0; i < ps.trickScore; i++) {
            player.wonTrick();
        }
        player.increaseScore(ps.score);
        return player;
    }

    private static Round toRound(GameStateSnapshot snapshot, List<Player> players, int width, int height, Handler handler) {
        RoundSnapshot rs = snapshot.round;
        Suit trump = rs.trumpCard.suit;
        Card trumpCard = new Card(trump, rs.trumpCard.value);
        // Every player's hand size equals the round's card count for the
        // whole round (see Round.deal()) -- this mirrors Game.numCardsThisRound()
        // exactly (10 - roundIndex), the only other place this formula lives.
        int numCards = 10 - snapshot.roundIndex;

        Round round = new Round(numCards, players, rs.currentPlayer, width, height, handler,
                trumpCard, trump, rs.trumpBroken);

        if (rs.currentTrick != null) {
            TrickSnapshot ts = rs.currentTrick;
            Trick trick = new Trick(players, ts.trickStartPlayer, ts.currentPlayer, trump, rs.trumpBroken,
                    ts.leadingSuit, ts.cardsPlayedBySeat, width, height, handler);
            round.setCurrentTrick(trick);
        }

        return round;
    }
}
