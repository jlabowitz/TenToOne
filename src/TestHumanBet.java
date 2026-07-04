import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;

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

    /**
     * Coverage gap noted in QA's follow-up on the achievement-toast
     * click-to-dismiss wiring: Human.bet()'s click loop got the identical
     * guard RulesView.showBlocking/AchievementsView.showBlocking/
     * Game.awaitPlayAgain already have dedicated tests for (see
     * TestRulesView.showBlockingDismissesToastHotspotClickBeforeCheckingBackButton
     * for the exact pattern this mirrors) -- QA manually verified this one
     * live and confirmed the guard is byte-for-byte identical at all three
     * Human sites, but no automated test locked it in here. A background
     * thread delivers a click inside the toast's dismiss band before the
     * real Bet-button click; if the dismiss click fell through to
     * controlAt() unhandled instead of being consumed, the stepper's value
     * would still be a valid bet (0), so this asserts on the toast's state
     * rather than relying on a hang -- the loop would resolve either way,
     * dismissed-or-not, unlike the Back-button/Play-Again variants where an
     * unhandled dismiss click cannot resolve the loop at all.
     */
    @Test(timeout = 5000)
    public void betDismissesToastHotspotClickBeforeCheckingStepperControls() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Test Achievement");
        toast.tick();
        assertTrue("toast must actually be showing for isToastHotspot() to fire", toast.isShowingSomething());

        Human human = new Human("Test", mouseInput, handler, toast, SaveData.defaults());
        Hand hand = new Hand(840, 480, ID.HUMAN);
        hand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        human.setHand(hand);

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 400, 20); // inside the toast's dismiss band (y < 50), clear of the stepper row
            deliverClick(mouseInput, 760, 600); // the real Bet button (default stepper value 0 is a valid bet here)
        });
        clicker.start();

        human.bet(Suit.HEARTS);
        clicker.join();

        assertFalse("the first click (inside the toast band) must dismiss the toast rather than being ignored",
                toast.isShowingSomething());
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
