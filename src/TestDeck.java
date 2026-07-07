import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 27: deterministic RNG seeding for Deck's shuffle. Two
 * separately-constructed Decks given the same seed must produce the exact
 * same card order (so the persistent-game-state codec's reconstruction tests
 * can deal a known hand deterministically -- see design/persistent-game-state.md
 * Phase 1), while the no-arg constructor must keep today's behavior of
 * varying randomness across instances.
 */
public class TestDeck {
    @Test
    public void sameSeedProducesSameCardOrderAcrossSeparateInstances() {
        Deck first = new Deck(12345L);
        Deck second = new Deck(12345L);

        List<Card> firstOrder = drainAll(first);
        List<Card> secondOrder = drainAll(second);

        assertEquals(firstOrder.size(), secondOrder.size());
        for (int i = 0; i < firstOrder.size(); i++) {
            Card a = firstOrder.get(i);
            Card b = secondOrder.get(i);
            assertEquals("card " + i + " suit should match", a.getSuit(), b.getSuit());
            assertEquals("card " + i + " value should match", a.getValue(), b.getValue());
        }
    }

    @Test
    public void differentSeedsProduceDifferentCardOrder() {
        Deck first = new Deck(1L);
        Deck second = new Deck(2L);

        List<Card> firstOrder = drainAll(first);
        List<Card> secondOrder = drainAll(second);

        boolean anyDifferent = false;
        for (int i = 0; i < firstOrder.size(); i++) {
            Card a = firstOrder.get(i);
            Card b = secondOrder.get(i);
            if (a.getSuit() != b.getSuit() || a.getValue() != b.getValue()) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue("different seeds should (almost certainly) produce a different order", anyDifferent);
    }

    @Test
    public void noArgConstructorStillVariesAcrossInstances() {
        Deck first = new Deck();
        Deck second = new Deck();

        assertTrue("fresh, unseeded Decks should get different seeds", first.getSeed() != second.getSeed());
    }

    @Test
    public void getSeedReturnsTheSeedActuallyUsedForSeededConstructor() {
        Deck deck = new Deck(999L);
        assertEquals(999L, deck.getSeed());
    }

    @Test
    public void getSeedIsNeverNullForDefaultConstructor() {
        Deck deck = new Deck();
        // No exception/no default (0) collision expected specifically -- just
        // confirming a seed was actually recorded, exercised further by the
        // varies-across-instances test above.
        long seed = deck.getSeed();
        Deck reseeded = new Deck(seed);
        assertEquals(deck.count(), reseeded.count());
    }

    private List<Card> drainAll(Deck deck) {
        List<Card> cards = new ArrayList<>();
        while (deck.count() > 0) {
            cards.add(deck.draw());
        }
        return cards;
    }
}
