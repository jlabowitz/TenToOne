import java.awt.Point;
import java.util.List;

public class Human extends Player{
    /** ROADMAP item 27/persistent-game-state design doc §2a -- see AI_Easy.ARCHETYPE_ID's doc. */
    static final String ARCHETYPE_ID = "human";

    private final MouseInput mouseInput;
    private final Handler handler;
    //ROADMAP follow-up: threaded through so the three click loops below can
    //check the toast's click-to-dismiss hotspot (achievementToast) and open
    //the in-game Achievements view (saveData), mirroring exactly how the
    //existing Rules checks already work -- see Game's construction of this
    //class for the shared instances passed in.
    private final AchievementToast achievementToast;
    private final SaveData saveData;
    /**
     * design/persistent-game-state.md Phase 7: invoked right before and right
     * after each of this class's three blocking click-loops (bet/playCard/
     * nextTrick) -- the moments closest to "the app could plausibly be closed
     * right now," per the design doc §6. A plain Runnable rather than a new
     * named functional interface -- this class already threads several
     * single-purpose collaborators as plain constructor params, and a
     * zero-arg "do the checkpoint save" callback needs no more shape than
     * Runnable already provides.
     */
    private final Runnable checkpointSaver;

    /**
     * ROADMAP item 10 (hamburger menu): gameSettings is read by the Settings
     * item (SettingsView.showBlocking mutates it directly); onReturnToMenu/
     * onRestartConfirmed are Game-owned callbacks invoked by
     * handleHamburgerMenu() below for the Menu/Restart-confirmed items --
     * both are expected to throw an unchecked signal (ReturnToMenuSignal/
     * RestartGameSignal respectively) that unwinds up to Game.play()'s own
     * outer loop; see those classes' docs. Plain Runnables, same reasoning as
     * checkpointSaver's own doc above -- no dedicated functional interface
     * needed for a zero-arg "do the thing" callback.
     */
    private final GameSettings gameSettings;
    private final Runnable onReturnToMenu;
    private final Runnable onRestartConfirmed;

    /**
     * Convenience overload for callers that don't need checkpoint saves
     * (every existing test, and any future non-gameplay construction) --
     * delegates to the 6-arg constructor with a no-op callback.
     */
    public Human(String name, MouseInput mouseInput, Handler handler, AchievementToast achievementToast, SaveData saveData) {
        this(name, mouseInput, handler, achievementToast, saveData, () -> {});
    }

    /**
     * Convenience overload for callers that don't need the hamburger-menu
     * collaborators (every existing test predating ROADMAP item 10) --
     * delegates to the full constructor with a fresh default GameSettings and
     * no-op Menu/Restart callbacks, same "extend the existing convenience-
     * overload pattern" this class already established for checkpointSaver.
     */
    public Human(String name, MouseInput mouseInput, Handler handler, AchievementToast achievementToast,
                 SaveData saveData, Runnable checkpointSaver) {
        this(name, mouseInput, handler, achievementToast, saveData, checkpointSaver,
                new GameSettings(), () -> {}, () -> {});
    }

    public Human(String name, MouseInput mouseInput, Handler handler, AchievementToast achievementToast,
                 SaveData saveData, Runnable checkpointSaver, GameSettings gameSettings,
                 Runnable onReturnToMenu, Runnable onRestartConfirmed) {
        super(name);
        this.mouseInput = mouseInput;
        this.handler = handler;
        this.achievementToast = achievementToast;
        this.saveData = saveData;
        this.checkpointSaver = checkpointSaver;
        this.gameSettings = gameSettings;
        this.onReturnToMenu = onReturnToMenu;
        this.onRestartConfirmed = onRestartConfirmed;
        id = ID.HUMAN;
    }

    @Override
    public String archetypeId() {
        return ARCHETYPE_ID;
    }

    @Override
    public void bet(BettingContext context) {
        int sumOfPriorBets = context.sumOfPriorBets();
        boolean isLastBettor = context.isLastBettor();
        boolean totalBetsCannotEqualTricks = context.totalBetsCannotEqualTricks();
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
        // design/persistent-game-state.md Phase 7: right before this blocking
        // click-loop starts.
        checkpointSaver.run();
        try {
            betLoop:
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (stepper.isHamburgerHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "hamburger icon (open menu)");
                    handleHamburgerMenu();
                    mouseInput.clearClicks();
                    continue;
                }
                BetStepper.Control control = stepper.controlAt(click.x, click.y);
                if (control == null) {
                    InteractionLog.logClick(click.x, click.y, "no control matched");
                    continue;
                }
                switch (control) {
                    case DECREMENT:
                        InteractionLog.logClick(click.x, click.y, "BetStepper.DECREMENT");
                        stepper.decrement();
                        continue;
                    case INCREMENT:
                        InteractionLog.logClick(click.x, click.y, "BetStepper.INCREMENT");
                        stepper.increment();
                        continue;
                    case BET:
                        InteractionLog.logClick(click.x, click.y, "BetStepper.BET");
                        int candidate = stepper.getValue();
                        if (Round.isLegalBet(candidate, maxBet, sumOfPriorBets, maxBet,
                                isLastBettor, totalBetsCannotEqualTricks)) {
                            setBet(candidate);
                            break betLoop;
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
        // design/persistent-game-state.md Phase 7: right after the click-loop resolves.
        checkpointSaver.run();
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
        // design/persistent-game-state.md Phase 7: right before this blocking
        // click-loop starts.
        checkpointSaver.run();
        Card played;
        try {
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (feedback.isHamburgerHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "hamburger icon (open menu)");
                    handleHamburgerMenu();
                    mouseInput.clearClicks();
                    continue;
                }
                Card card = getHand().cardAt(click.x, click.y);
                if (card == null) {
                    InteractionLog.logClick(click.x, click.y, "no control matched");
                    continue;
                }
                if (!legal.contains(card)) {
                    InteractionLog.logClick(click.x, click.y, "hand card " + card + " (illegal)");
                    feedback.trigger(illegalReason(cardsPlayed, leading));
                    System.out.println("The " + card + " is not a legal play.");
                    continue;
                }
                InteractionLog.logClick(click.x, click.y, "hand card " + card);
                getHand().playCard(card);
                System.out.println(getName() + " played the " + card);
                played = card;
                break;
            }
        } finally {
            handler.removeObject(feedback);
        }
        // design/persistent-game-state.md Phase 7: right after the click-loop resolves.
        checkpointSaver.run();
        return played;
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
        mouseInput.clearClicks();
        // design/persistent-game-state.md Phase 7: right before this blocking
        // click-loop starts.
        checkpointSaver.run();
        try {
            while (true) {
                Point click = mouseInput.awaitClick();
                if (achievementToast.isToastHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "AchievementToast (dismiss)");
                    achievementToast.dismiss();
                    continue;
                }
                if (prompt.isHamburgerHotspot(click.x, click.y)) {
                    InteractionLog.logClick(click.x, click.y, "hamburger icon (open menu)");
                    handleHamburgerMenu();
                    mouseInput.clearClicks();
                    continue;
                }
                InteractionLog.logClick(click.x, click.y, "NextTrickPrompt (continue)");
                break;
            }
        } finally {
            handler.removeObject(prompt);
        }
        // design/persistent-game-state.md Phase 7: right after the click-loop resolves.
        checkpointSaver.run();
    }

    /**
     * ROADMAP item 10: shared dispatch for the hamburger menu, called
     * identically from all three of this class's blocking click-loops
     * (bet/playCard/nextTrick) once a hamburger-hotspot click is detected --
     * extracted here rather than tripled, unlike the small per-loop Rules/
     * Achievements checks above (those stayed inline, matching this
     * codebase's existing duplication convention for tiny one-line hotspot
     * checks; this dispatch is bigger and has real branching, so it's pulled
     * out once instead).
     *
     * MENU and RESTART (once confirmed by HamburgerMenu itself) invoke
     * Game-owned callbacks that are expected to throw an unchecked signal
     * (ReturnToMenuSignal/RestartGameSignal) -- this method does not catch
     * either; they propagate straight up through bet()/playCard()/
     * nextTrick()'s own try/finally blocks (running those methods' finally
     * cleanup along the way) to Game.play()'s own outer loop. See
     * ReturnToMenuSignal/RestartGameSignal's own docs.
     */
    private void handleHamburgerMenu() {
        HamburgerMenu.Selection selection = HamburgerMenu.showBlocking(handler, mouseInput, achievementToast);
        if (selection == null) {
            return;
        }
        switch (selection) {
            case RULES:
                RulesView.showBlocking(handler, mouseInput, achievementToast);
                break;
            case ACHIEVEMENTS:
                AchievementsView.showBlocking(handler, mouseInput, saveData, achievementToast);
                break;
            case SETTINGS:
                SettingsView.showBlocking(handler, mouseInput, achievementToast, gameSettings);
                break;
            case PAUSE:
                PauseView.showBlocking(handler, mouseInput, achievementToast);
                break;
            case MENU:
                onReturnToMenu.run();
                break;
            case RESTART:
                onRestartConfirmed.run();
                break;
        }
    }
}
