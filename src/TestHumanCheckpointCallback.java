import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * design/persistent-game-state.md Phase 7: Human.bet()/playCard()/nextTrick()
 * must each invoke the checkpoint-save callback exactly twice -- once right
 * before the blocking click-loop starts, once right after it resolves --
 * mirroring the design doc §6 checkpoint cadence. The 6-arg constructor is
 * additive (see the 5-arg overload every other Human test still uses); this
 * file exercises only the new collaborator.
 */
public class TestHumanCheckpointCallback {

    @Test(timeout = 5000)
    public void betInvokesCheckpointBeforeAndAfterTheClickLoop() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        AtomicInteger checkpointCalls = new AtomicInteger(0);

        Human human = new Human("Test", mouseInput, handler, toast, SaveData.defaults(), checkpointCalls::incrementAndGet);
        Hand hand = new Hand(840, 480, ID.HUMAN);
        hand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        human.setHand(hand);

        assertEquals(0, checkpointCalls.get());

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 760, 600); // the real Bet button (default stepper value 0)
        });
        clicker.start();

        human.bet(new BettingContext(Suit.HEARTS, List.of(), 2, true, false, true));
        clicker.join();

        assertEquals(2, checkpointCalls.get());
    }

    @Test(timeout = 5000)
    public void playCardInvokesCheckpointBeforeAndAfterTheClickLoop() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        AtomicInteger checkpointCalls = new AtomicInteger(0);

        Human human = new Human("Test", mouseInput, handler, toast, SaveData.defaults(), checkpointCalls::incrementAndGet);
        Hand hand = new Hand(840, 480, ID.HUMAN);
        Card card = new Card(Suit.HEARTS, CardValue.ACE);
        hand.addCard(card);
        hand.setX(0);
        hand.setY(0);
        human.setHand(hand);

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // Card 0 of a 1-card hand lays out at x = handX*0/1 = handX, y = handY.
            deliverClick(mouseInput, 5, 5);
        });
        clicker.start();

        human.playCard(List.of(), null, Suit.HEARTS, true);
        clicker.join();

        assertEquals(2, checkpointCalls.get());
    }

    @Test(timeout = 5000)
    public void nextTrickInvokesCheckpointBeforeAndAfterTheClickLoop() throws InterruptedException {
        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();
        AtomicInteger checkpointCalls = new AtomicInteger(0);

        Human human = new Human("Test", mouseInput, handler, toast, SaveData.defaults(), checkpointCalls::incrementAndGet);

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 400, 400);
        });
        clicker.start();

        human.nextTrick();
        clicker.join();

        assertEquals(2, checkpointCalls.get());
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
