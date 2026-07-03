import java.awt.Point;
import java.util.List;

public class Human extends Player{
    private final MouseInput mouseInput;
    private final Handler handler;

    public Human(String name, MouseInput mouseInput, Handler handler) {
        super(name);
        this.mouseInput = mouseInput;
        this.handler = handler;
        id = ID.HUMAN;
    }


    @Override
    public void bet(Suit trump) {
        int maxBet = getHand().getNumCards();
        System.out.println(getHand());
        System.out.println(getName() + ", click the stepper to choose your bet (0-" + maxBet + "), then click Bet.");

        BetStepper stepper = new BetStepper(maxBet);
        handler.addObject(stepper);
        mouseInput.clearClicks();
        try {
            while (true) {
                Point click = mouseInput.awaitClick();
                BetStepper.Control control = stepper.controlAt(click.x, click.y);
                if (control == null) {
                    continue;
                }
                switch (control) {
                    case DECREMENT:
                        stepper.decrement();
                        continue;
                    case INCREMENT:
                        stepper.increment();
                        continue;
                    case BET:
                        if (isValidBet(stepper.getValue(), maxBet)) {
                            setBet(stepper.getValue());
                            return;
                        }
                        continue;
                }
            }
        } finally {
            handler.removeObject(stepper);
        }
    }

    static boolean isValidBet(int bet, int maxBet) {
        return bet >= 0 && bet <= maxBet;
    }

    @Override
    public Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        System.out.println(getHand());
        System.out.println(getName() + ", click the card you want to play.");

        List<Card> legal = legalCards(cardsPlayed, leading, trump, trumpBroken);
        mouseInput.clearClicks();
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        handler.addObject(feedback);
        try {
            while (true) {
                Point click = mouseInput.awaitClick();
                Card card = getHand().cardAt(click.x, click.y);
                if (card == null) {
                    continue;
                }
                if (!legal.contains(card)) {
                    feedback.trigger(illegalReason(cardsPlayed, leading));
                    System.out.println("The " + card + " is not a legal play.");
                    continue;
                }
                getHand().playCard(card);
                System.out.println(getName() + " played the " + card);
                return card;
            }
        } finally {
            handler.removeObject(feedback);
        }
    }

    /**
     * Player.legalCards() has two independent branches (leading vs.
     * following a trick), so an illegal click has two distinct reasons, not
     * one -- a single generic message (or one that always says "follow
     * suit") would actively mislead in the trump-lead case. Derived
     * statically from cardsPlayed.isEmpty(), the same condition
     * legalCards() itself branches on -- no new state needed.
     */
    static String illegalReason(List<Card> cardsPlayed, Suit leading) {
        if (cardsPlayed.isEmpty()) {
            return "Trump hasn't been broken yet -- lead a different suit.";
        }
        if (leading != null) {
            return "You must follow suit -- play a " + leading.getDisplayName() + " card.";
        }
        return "That card can't be played right now.";
    }

    @Override
    public void nextTrick() {
        System.out.println("Click anywhere to move on to the next trick.");
        NextTrickPrompt prompt = new NextTrickPrompt();
        handler.addObject(prompt);
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                if (prompt.isRulesHotspot(click.x, click.y)) {
                    RulesView.showBlocking(handler, mouseInput);
                    mouseInput.clearClicks();
                    continue;
                }
                return;
            }
        } finally {
            handler.removeObject(prompt);
        }
    }
}
