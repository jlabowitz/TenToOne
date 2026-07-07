import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * design/persistent-game-state.md Phase 5: round-trips GameStateSnapshot
 * through GameStateCodec.toSnapshot/fromSnapshot at the three shapes the
 * design doc calls out -- between rounds (no Round), mid-bet (Round exists,
 * no current trick), and mid-trick (some cards already played this trick).
 * The mid-trick case is the highest-risk piece: it asserts a reconstructed
 * Trick, resumed partway through, produces the exact same winner as the same
 * starting hands played uninterrupted.
 */
public class TestGameStateCodec {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    /** Deals numCards-card hands to each of numPlayers players from a seeded Deck, mirroring Round.deal()'s draw order exactly. */
    private static List<List<Card>> dealHands(long seed, int numCards, int numPlayers) {
        Deck deck = new Deck(seed);
        List<List<Card>> hands = new ArrayList<>();
        for (int p = 0; p < numPlayers; p++) {
            hands.add(new ArrayList<>());
        }
        for (int i = 0; i < numCards; i++) {
            for (int p = 0; p < numPlayers; p++) {
                hands.get(p).add(deck.draw());
            }
        }
        return hands;
    }

    private static Hand handOf(List<Card> cards, ID id) {
        Hand hand = new Hand(WIDTH, HEIGHT, id);
        for (Card card : cards) {
            hand.addCard(card);
        }
        return hand;
    }

    // --- between rounds: no Round at all ---

    @Test
    public void betweenRoundsSnapshotRoundTripsPlayerStateWithNoRound() throws GameStateReconstructionException {
        AI_Easy easy = new AI_Easy("Easy Bot");
        easy.setHand(handOf(List.of(new Card(Suit.HEARTS, CardValue.ACE)), ID.AI));
        easy.increaseScore(42);

        AI_Medium medium = new AI_Medium("Medium Bot", AIPersonality.MEDIUM_BOLD);
        medium.setHand(handOf(List.of(new Card(Suit.CLUBS, CardValue.KING)), ID.AI));
        medium.increaseScore(17);

        List<Player> players = List.of(easy, medium);

        GameStateSnapshot snapshot = GameStateCodec.toSnapshot(3, 1, 2, true, players, null);

        assertNull(snapshot.round);
        assertEquals(3, snapshot.roundIndex);
        assertEquals(1, snapshot.roundStartingPlayer);
        assertEquals(2, snapshot.roundsHitBonusThisGame);
        assertTrue(snapshot.wasSoleLastAtHalfway);

        GameStateCodec.Reconstructed reconstructed = GameStateCodec.fromSnapshot(
                snapshot, WIDTH, HEIGHT, new Handler(), new MouseInput(), new AchievementToast(),
                SaveData.defaults(), () -> {});

        assertNull(reconstructed.round);
        assertEquals(2, reconstructed.players.size());

        Player rebuiltEasy = reconstructed.players.get(0);
        assertTrue(rebuiltEasy instanceof AI_Easy);
        assertEquals("Easy Bot", rebuiltEasy.getName());
        assertEquals("ai_easy", rebuiltEasy.archetypeId());
        assertEquals(42, rebuiltEasy.getScore());
        assertEquals(1, rebuiltEasy.getHand().getNumCards());
        assertEquals(CardValue.ACE, rebuiltEasy.getHand().getCard(0).getValue());

        Player rebuiltMedium = reconstructed.players.get(1);
        assertTrue(rebuiltMedium instanceof AI_Medium);
        assertEquals("medium_bold", rebuiltMedium.archetypeId());
        assertEquals(17, rebuiltMedium.getScore());
    }

    @Test
    public void humanArchetypeIsReconstructedDirectlyNotViaRegistry() throws GameStateReconstructionException {
        Human original = new Human("Alice", new MouseInput(), new Handler(), new AchievementToast(), SaveData.defaults());
        original.setHand(handOf(List.of(new Card(Suit.SPADES, CardValue.TWO)), ID.HUMAN));

        GameStateSnapshot snapshot = GameStateCodec.toSnapshot(0, 0, 0, false, List.of(original), null);
        assertEquals("human", snapshot.players.get(0).archetypeId);

        GameStateCodec.Reconstructed reconstructed = GameStateCodec.fromSnapshot(
                snapshot, WIDTH, HEIGHT, new Handler(), new MouseInput(), new AchievementToast(),
                SaveData.defaults(), () -> {});

        Player rebuilt = reconstructed.players.get(0);
        assertTrue(rebuilt instanceof Human);
        assertEquals("Alice", rebuilt.getName());
    }

    @Test
    public void unknownArchetypeIdFailsTheWholeLoad() {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        PlayerSnapshot ps = new PlayerSnapshot();
        ps.name = "Mystery";
        ps.archetypeId = "not_a_real_archetype";
        ps.hand = new ArrayList<>();
        snapshot.players.add(ps);

        try {
            GameStateCodec.fromSnapshot(snapshot, WIDTH, HEIGHT, new Handler(), new MouseInput(),
                    new AchievementToast(), SaveData.defaults(), () -> {});
            fail("an unknown archetypeId must fail the whole load, not partially reconstruct");
        } catch (GameStateReconstructionException expected) {
            // expected
        }
    }

    // --- mid-bet: Round exists, no current trick, bets partially entered ---

    @Test
    public void midBetSnapshotRoundTripsPartialBetsAndRoundState() throws GameStateReconstructionException {
        List<List<Card>> hands = dealHands(777L, 4, 2);
        AI_Zombie a = new AI_Zombie("A");
        AI_Zombie b = new AI_Zombie("B");
        a.setHand(handOf(hands.get(0), ID.AI));
        b.setHand(handOf(hands.get(1), ID.AI));
        List<Player> players = List.of(a, b);

        a.setBet(2); // already bet
        // b has not bet yet -- hasBet() stays false

        Card trumpCard = new Card(Suit.HEARTS, CardValue.KING);
        Round round = new Round(4, players, 0, WIDTH, HEIGHT, new Handler(),
                trumpCard, Suit.HEARTS, false);

        assertNull("no trick has started yet", round.getCurrentTrick());

        GameStateSnapshot snapshot = GameStateCodec.toSnapshot(2, 0, 0, false, players, round);

        assertNotNullRound(snapshot);
        assertNull(snapshot.round.currentTrick);
        assertEquals(Suit.HEARTS, snapshot.round.trumpCard.suit);
        assertEquals(CardValue.KING, snapshot.round.trumpCard.value);
        assertFalse(snapshot.round.trumpBroken);
        assertTrue(snapshot.players.get(0).hasBet);
        assertEquals(2, snapshot.players.get(0).bet);
        assertFalse(snapshot.players.get(1).hasBet);

        GameStateCodec.Reconstructed reconstructed = GameStateCodec.fromSnapshot(
                snapshot, WIDTH, HEIGHT, new Handler(), new MouseInput(), new AchievementToast(),
                SaveData.defaults(), () -> {});

        assertNull(reconstructed.round.getCurrentTrick());
        assertEquals(Suit.HEARTS, reconstructed.round.getTrump());
        assertFalse(reconstructed.round.getTrumpBroken());
        Player rebuiltA = reconstructed.players.get(0);
        Player rebuiltB = reconstructed.players.get(1);
        assertTrue(rebuiltA.hasBet());
        assertEquals(2, rebuiltA.getBet());
        assertFalse(rebuiltB.hasBet());
        assertEquals(4, rebuiltA.getHand().getNumCards());
        assertEquals(4, rebuiltB.getHand().getNumCards());
    }

    private static void assertNotNullRound(GameStateSnapshot snapshot) {
        if (snapshot.round == null) {
            fail("expected a non-null RoundSnapshot");
        }
    }

    // --- mid-trick: the highest-risk case ---

    /**
     * Builds a 3-card-hand, 3-player round with a known trump, plays the
     * first two seats' cards directly (bypassing Trick, mirroring exactly
     * what Trick.play() itself would have done -- AI_Zombie's strategy is
     * deterministic, always legalCards.get(0)), and asserts that resuming
     * from a TrickSnapshot captured at that point produces the same
     * cardsPlayed/winner as an identically-dealt trick played straight
     * through with no interruption.
     */
    @Test
    public void midTrickReconstructionContinuesIdenticallyToUninterruptedPlay() throws GameStateReconstructionException {
        long seed = 20260707L;
        List<List<Card>> hands = dealHands(seed, 3, 3);

        // --- control run: deal identical hands, play the trick straight through, uninterrupted ---
        AI_Zombie controlA = new AI_Zombie("A");
        AI_Zombie controlB = new AI_Zombie("B");
        AI_Zombie controlC = new AI_Zombie("C");
        controlA.setHand(handOf(new ArrayList<>(hands.get(0)), ID.AI));
        controlB.setHand(handOf(new ArrayList<>(hands.get(1)), ID.AI));
        controlC.setHand(handOf(new ArrayList<>(hands.get(2)), ID.AI));
        List<Player> controlPlayers = List.of(controlA, controlB, controlC);
        Trick controlTrick = new Trick(controlPlayers, 0, Suit.SPADES, false, WIDTH, HEIGHT, new Handler());
        List<Card> controlCardsPlayed = controlTrick.play();
        int controlWinner = Round.determineTrickWinner(controlCardsPlayed, Suit.SPADES);

        // --- experiment: play only the first 2 seats directly, snapshot, reconstruct, resume ---
        AI_Zombie a = new AI_Zombie("A");
        AI_Zombie b = new AI_Zombie("B");
        AI_Zombie c = new AI_Zombie("C");
        a.setHand(handOf(new ArrayList<>(hands.get(0)), ID.AI));
        b.setHand(handOf(new ArrayList<>(hands.get(1)), ID.AI));
        c.setHand(handOf(new ArrayList<>(hands.get(2)), ID.AI));
        List<Player> players = List.of(a, b, c);

        List<Card> cardsPlayedSoFar = new ArrayList<>();
        Card firstCard = a.playCard(cardsPlayedSoFar, null, Suit.SPADES, false);
        cardsPlayedSoFar.add(firstCard);
        Suit leading = firstCard.getSuit();
        Card secondCard = b.playCard(cardsPlayedSoFar, leading, Suit.SPADES, false);
        cardsPlayedSoFar.add(secondCard);

        Card trumpCard = new Card(Suit.SPADES, CardValue.TWO);
        Round round = new Round(3, players, 0, WIDTH, HEIGHT, new Handler(), trumpCard, Suit.SPADES, false);
        Trick liveTrick = new Trick(players, 0, 2, Suit.SPADES, false, leading,
                List.of(new SeatCardPlay(0, new CardSnapshot(firstCard.getSuit(), firstCard.getValue())),
                        new SeatCardPlay(1, new CardSnapshot(secondCard.getSuit(), secondCard.getValue()))),
                WIDTH, HEIGHT, new Handler());
        round.setCurrentTrick(liveTrick);

        GameStateSnapshot snapshot = GameStateCodec.toSnapshot(0, 0, 0, false, players, round);

        assertNotNullRound(snapshot);
        assertTrue(snapshot.round.currentTrick != null);
        assertEquals(2, snapshot.round.currentTrick.cardsPlayedBySeat.size());
        assertEquals(2, snapshot.round.currentTrick.currentPlayer);
        assertEquals(0, snapshot.round.currentTrick.trickStartPlayer);
        assertEquals(leading, snapshot.round.currentTrick.leadingSuit);

        GameStateCodec.Reconstructed reconstructed = GameStateCodec.fromSnapshot(
                snapshot, WIDTH, HEIGHT, new Handler(), new MouseInput(), new AchievementToast(),
                SaveData.defaults(), () -> {});

        Trick resumedTrick = reconstructed.round.getCurrentTrick();
        assertEquals(2, resumedTrick.getCardsPlayedBySeat().size());
        assertEquals(2, resumedTrick.getCurrentPlayer());

        List<Card> resumedCardsPlayed = resumedTrick.play();
        int resumedWinner = Round.determineTrickWinner(resumedCardsPlayed, Suit.SPADES);

        assertEquals("resumed trick must produce the same number of cards played",
                controlCardsPlayed.size(), resumedCardsPlayed.size());
        for (int i = 0; i < controlCardsPlayed.size(); i++) {
            assertEquals("card " + i + " suit must match the uninterrupted control run",
                    controlCardsPlayed.get(i).getSuit(), resumedCardsPlayed.get(i).getSuit());
            assertEquals("card " + i + " value must match the uninterrupted control run",
                    controlCardsPlayed.get(i).getValue(), resumedCardsPlayed.get(i).getValue());
        }
        assertEquals("resumed trick's winner seat must match the uninterrupted control run",
                controlWinner, resumedWinner);
    }
}
