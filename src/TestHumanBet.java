import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Human.isValidBet, the pure validation predicate backing
 * Human.bet's console input loop.
 *
 * A bet is valid iff it is within [0, maxBet] -- you can't bet negative
 * tricks, and you can't bet more tricks than cards in your hand (maxBet is
 * the hand size for the round).
 */
public class TestHumanBet {

    @Test
    public void zeroIsValid() {
        assertTrue(Human.isValidBet(0, 10));
    }

    @Test
    public void middleValueIsValid() {
        assertTrue(Human.isValidBet(5, 10));
    }

    @Test
    public void exactlyMaxBetIsValid() {
        assertTrue(Human.isValidBet(10, 10));
    }

    @Test
    public void negativeBetIsInvalid() {
        assertFalse(Human.isValidBet(-1, 10));
    }

    @Test
    public void betGreaterThanMaxIsInvalid() {
        assertFalse(Human.isValidBet(11, 10));
    }

    @Test
    public void oneLessThanMaxBetIsValid() {
        assertTrue(Human.isValidBet(9, 10));
    }

    @Test
    public void oneMoreThanMaxBetIsInvalid() {
        assertFalse(Human.isValidBet(11, 10));
    }

    @Test
    public void zeroMaxBetOnlyZeroIsValid() {
        assertTrue(Human.isValidBet(0, 0));
        assertFalse(Human.isValidBet(1, 0));
        assertFalse(Human.isValidBet(-1, 0));
    }
}
