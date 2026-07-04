import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Wiring test for Human.playCard()'s click loop (coverage gap noted in QA's
 * follow-up on the achievement-toast click-to-dismiss wiring; see
 * TestHumanBet.betDismissesToastHotspotClickBeforeCheckingStepperControls and
 * TestRulesView.showBlockingDismissesToastHotspotClickBeforeCheckingBackButton
 * for the pattern this mirrors, and TestHumanIllegalReason for playCard()'s
 * other extracted pure logic). QA manually verified only Human.bet()'s
 * dismiss live and confirmed the guard is byte-for-byte identical at all
 * three Human sites; this locks playCard()'s copy in with an automated test
 * too.
 */
public class TestHumanPlayCard {

    @Test(timeout = 5000)
    public void playCardDismissesToastHotspotClickBeforeCheckingCards() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        toast.activate();
        toast.enqueue("Test Achievement");
        toast.tick();
        assertTrue("toast must actually be showing for isToastHotspot() to fire", toast.isShowingSomething());

        Human human = new Human("Test", mouseInput, handler, toast, SaveData.defaults());
        // Single-card hand, trump already broken: legalCards() returns every
        // card in hand regardless of leading/trump, so this one card is
        // guaranteed legal -- see Player.legalCards()'s cardsPlayed.isEmpty()
        // && trumpBroken branch.
        Hand hand = new Hand(840, 480, ID.HUMAN);
        Card card = new Card(Suit.HEARTS, CardValue.ACE);
        hand.addCard(card);
        human.setHand(hand);

        List<Card> cardsPlayed = Collections.emptyList();

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 400, 20); // inside the toast's dismiss band (y < 50), clear of the hand
            deliverClick(mouseInput, 30, 530); // the real card click (card 0 of a 1-card hand -- see TestHand)
        });
        clicker.start();

        Card played = human.playCard(cardsPlayed, null, Suit.HEARTS, true);
        clicker.join();

        assertFalse("the first click (inside the toast band) must dismiss the toast rather than being ignored",
                toast.isShowingSomething());
        assertSame("the real click must still resolve to the actual card played", card, played);
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
