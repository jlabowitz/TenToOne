import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Deck {

    private List<Card> cards = new ArrayList<>();
    private final long seed;

    /**
     * ROADMAP item 27: unseeded construction keeps today's behavior (fresh
     * randomness every run) by generating its own seed and delegating to the
     * seeded constructor -- so every Deck, seeded or not, ends up recording
     * which seed it used (see getSeed()).
     */
    public Deck() {
        this(new Random().nextLong());
    }

    /**
     * ROADMAP item 27: injectable-seed constructor. Two Decks built with the
     * same seed deal identical card orders -- used by the persistent-game-
     * state codec's tests (design/persistent-game-state.md Phase 1) to deal a
     * known hand deterministically instead of fighting real shuffle
     * randomness, and more generally to reproduce an exact deal for a bug
     * report.
     */
    public Deck(long seed) {
        this.seed = seed;
        generateDeck();
        shuffleDeck();
    }

    /** The seed actually used to shuffle this deck (see the seeded constructor's doc). */
    public long getSeed() {
        return seed;
    }

    private void generateDeck() {
        Suit[] suits = Suit.values();
        CardValue[] values = CardValue.values();

        for (Suit suit : suits) {
            for (CardValue value : values) {
                cards.add(new Card(suit, value));
            }
        }
    }

    private void shuffleDeck() {
        Collections.shuffle(cards, new Random(seed));
    }

    public Card draw() {
        assert cards.size() > 0 : "There are no cards in the deck";
        return cards.remove(0);
    }

    public int count() {
        return cards.size();
    }

    @Override
    public String toString() {
        return cards.toString();
    }
}
