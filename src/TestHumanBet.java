import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Human.bet()'s click-loop wiring. The pure bet-legality predicate
 * this loop checks (formerly Human.isValidBet, a range-only check) has been
 * retired in favor of the shared Round.isLegalBet -- see TestBetLegality.java
 * for that coverage (including the range-check cases migrated from here).
 * This file keeps only the tests that exercise Human.bet()'s click-handling
 * behavior itself, not the legality predicate in isolation.
 */
public class TestHumanBet {

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

        // not the last bettor, so the "total bets cannot equal tricks" rule
        // never constrains this bet -- irrelevant to what this test checks
        human.bet(new BettingContext(Suit.HEARTS, List.of(), 2, true, false, true));
        clicker.join();

        assertFalse("the first click (inside the toast band) must dismiss the toast rather than being ignored",
                toast.isShowingSomething());
    }

    /**
     * ROADMAP item 1 (design/ai-and-polish.md §3's flagged UX gap): BetStepper
     * clamps to [0, maxBet], so the forbidden "total bets cannot equal
     * tricks" value is the only way a human's Bet click can be illegal
     * today. Clicking Bet on that value must not finalize the bet (the loop
     * must keep running) -- a subsequent click on a legal value must still
     * succeed afterward.
     */
    @Test(timeout = 5000)
    public void lastBettorCannotFinalizeTheForbiddenBetValue() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Human human = new Human("Test", mouseInput, handler, toast, SaveData.defaults());
        Hand hand = new Hand(840, 480, ID.HUMAN);
        hand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        hand.addCard(new Card(Suit.HEARTS, CardValue.KING));
        human.setHand(hand); // maxBet = 2

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 715, 600); // INCREMENT -> stepper value 1 (the forbidden value here)
            deliverClick(mouseInput, 760, 600); // BET on the forbidden value -- must not finalize
            deliverClick(mouseInput, 715, 600); // INCREMENT -> stepper value 2 (legal)
            deliverClick(mouseInput, 760, 600); // BET on the now-legal value
        });
        clicker.start();

        // last bettor; sumOfPriorBets=1 in a 2-card round -> forbiddenBet = 2-1 = 1
        human.bet(new BettingContext(Suit.HEARTS, List.of(1), 2, false, true, true));
        clicker.join();

        assertEquals("the second Bet click (on the legal value) must be the one that finalizes",
                2, human.getBet());
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
