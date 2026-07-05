import java.awt.Point;
import java.util.List;

public class Human extends Player{
    private final MouseInput mouseInput;
    private final Handler handler;
    //ROADMAP follow-up: threaded through so the three click loops below can
    //check the toast's click-to-dismiss hotspot (achievementToast) and open
    //the in-game Achievements view (saveData), mirroring exactly how the
    //existing Rules checks already work -- see Game's construction of this
    //class for the shared instances passed in.
    private final AchievementToast achievementToast;
    private final SaveData saveData;

    public Human(String name, MouseInput mouseInput, Handler handler, AchievementToast achievementToast, SaveData saveData) {
        super(name);
        this.mouseInput = mouseInput;
        this.handler = handler;
        this.achievementToast = achievementToast;
        this.saveData = saveData;
        id = ID.HUMAN;
    }


    @Override
    public void bet(Suit trump, int sumOfPriorBets, boolean isLastBettor, boolean totalBetsCannotEqualTricks) {
        int maxBet = getHand().getNumCards();
        System.out.println(getHand());
        System.out.println(getName() + ", click the stepper to choose your bet (0-" + maxBet + "), then click Bet.");

        BetStepper stepper = new BetStepper(maxBet);
        handler.addObject(stepper);
        // ROADMAP item 1 (design/ai-and-polish.md §3's flagged UX gap):
        // BetStepper already clamps to [0, maxBet], so the range-check branch
        // of Round.isLegalBet is unreachable from this UI -- but the forbidden
        // "total bets can't equal tricks" value is an *interior* value the
        // stepper can absolutely still produce, so a click on it must not
        // silently no-op. Mirrors playCard()'s IllegalPlayFeedback usage
        // exactly (add-before/remove-after, same trigger()/render() pattern).
        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        handler.addObject(feedback);
        mouseInput.clearClicks();
        try {
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    achievementToast.dismiss();
                    continue;
                }
                if (stepper.isRulesHotspot(click.x, click.y)) {
                    RulesView.showBlocking(handler, mouseInput, achievementToast);
                    mouseInput.clearClicks();
                    continue;
                }
                if (stepper.isAchievementsHotspot(click.x, click.y)) {
                    AchievementsView.showBlocking(handler, mouseInput, saveData, achievementToast);
                    mouseInput.clearClicks();
                    continue;
                }
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
                        int candidate = stepper.getValue();
                        if (Round.isLegalBet(candidate, maxBet, sumOfPriorBets, maxBet,
                                isLastBettor, totalBetsCannotEqualTricks)) {
                            setBet(candidate);
                            return;
                        }
                        int forbiddenBet = maxBet - sumOfPriorBets;
                        feedback.trigger(illegalBetReason(forbiddenBet));
                        System.out.println(illegalBetReason(forbiddenBet));
                        continue;
                }
            }
        } finally {
            handler.removeObject(feedback);
            handler.removeObject(stepper);
        }
    }

    /**
     * Message shown via IllegalPlayFeedback when this human, as the round's
     * last bettor, clicks Bet on the one forbidden value (Round.isLegalBet's
     * "total bets cannot equal tricks" rule) -- pure static string builder,
     * mirroring illegalReason's shape below. Only ever reachable from
     * Human.bet()'s BET case, where isLegalBet having failed with a
     * stepper-clamped in-range candidate means this exact rule is why (see
     * bet()'s comment on BetStepper already ruling out the plain range
     * check).
     */
    static String illegalBetReason(int forbiddenBet) {
        return "Total bets can't equal " + forbiddenBet + " -- pick a different value.";
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
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    achievementToast.dismiss();
                    continue;
                }
                if (feedback.isRulesHotspot(click.x, click.y)) {
                    RulesView.showBlocking(handler, mouseInput, achievementToast);
                    mouseInput.clearClicks();
                    continue;
                }
                if (feedback.isAchievementsHotspot(click.x, click.y)) {
                    AchievementsView.showBlocking(handler, mouseInput, saveData, achievementToast);
                    mouseInput.clearClicks();
                    continue;
                }
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
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    achievementToast.dismiss();
                    continue;
                }
                if (prompt.isRulesHotspot(click.x, click.y)) {
                    RulesView.showBlocking(handler, mouseInput, achievementToast);
                    mouseInput.clearClicks();
                    continue;
                }
                if (prompt.isAchievementsHotspot(click.x, click.y)) {
                    AchievementsView.showBlocking(handler, mouseInput, saveData, achievementToast);
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
