import java.awt.*;
import java.awt.image.BufferStrategy;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/***
 Each game has up to 5 players and consists of 10 rounds with the number of cards in hand decreasing from 10 to 1.
 ***/
public class Game extends Canvas implements Runnable{
    @Serial
    private static final long serialVersionUID = 7694947508904043283L;
    public static final int WIDTH = 840, HEIGHT = WIDTH/12 * 9;
    private volatile Thread thread;
    //written by stop() (from either the game thread or an external caller)
    //and read every loop iteration by run() on the game thread -- volatile
    //so a write is guaranteed visible to run()'s while(running) check
    //without relying on stop()'s (now removed) synchronized lock for it.
    private volatile boolean running = false;
    private final Handler handler;
    private final MouseInput mouseInput;
    private final KeyInput keyInput;

    private final List<Player> players;
    private int roundIndex;
    private int roundStartingPlayer;
    private final int roundBonus = 10;

    /**
     * design/persistent-game-state.md Phase 4: promoted from play()'s local
     * variable so a checkpoint fired anywhere (e.g. from inside Human's
     * blocking click-loops, several stack frames deep) can reach the round
     * currently in progress -- and, via Round's own getCurrentTrick(), the
     * trick in progress too. Set at the top of each roundIndex loop
     * iteration; nulled out again as soon as playOneRound()'s round.playRound()
     * call returns, i.e. right after the round has actually finished (empty
     * hands, reset trick scores). Code-review fix: this used to stay set to
     * the just-finished Round for the rest of the loop-body iteration, which
     * meant the two checkpoints inside showRoundSummary() (called later in
     * the same iteration) wrote a non-null RoundSnapshot for a round that had
     * already ended -- violating GameStateSnapshot.round's own "null if
     * between rounds" contract. Nulling it here instead means anything
     * checkpointed for the rest of this iteration (currently just
     * showRoundSummary's two calls) correctly reports "between rounds."
     */
    private Round currentRound;

    //ROADMAP item 1 (play-again restart): kept so restartForNewGame() can
    //re-invoke the Start Screen name-capture seam a second time -- this was
    //a constructor parameter only before, never stored.
    private final List<String> aiNames;

    //ROADMAP item 2 (achievement system): loaded once at construction, saved
    //again after every round-end/game-end/name-submission hook -- see
    //SaveStore's class doc for why this is never deferred to process exit.
    private final SaveStore saveStore;
    private final SaveData saveData;

    //design/persistent-game-state.md Phase 6/7: mirrors saveStore/saveData's
    //own long-lived-for-the-session field pattern -- checkpoints are written
    //continuously throughout play (see saveGameStateCheckpoint()), not just
    //at round/game boundaries.
    private final GameStateStore gameStateStore;

    //ROADMAP item 1 (design/ai-and-polish.md §3): plain code-only config
    //holder, no UI reads/writes it yet -- see GameSettings' own class doc.
    //Held here (long-lived for the whole session, like saveData/
    //achievementToast) rather than constructed per-round, and passed into
    //each round's bet() call.
    private final GameSettings gameSettings = new GameSettings();

    //ROADMAP item 2: long-lived for the whole session (registered with the
    //Handler once in the constructor via keepOnTop(), never removed --
    //mirrors how `players` stay registered across Play-Again per
    //restartForNewGame()'s own doc) so an achievement queued during the
    //Start Screen (the Bapi easter egg fires at name-submission time,
    //before any Round exists) is never dropped. keepOnTop() (see Handler's
    //class doc) keeps it rendering above every full-canvas modal that comes
    //and goes over the session -- RoundSummaryPanel, GameOverBanner,
    //RulesView, AchievementsView, StartScreen -- not just whichever one
    //happened to exist when the toast was first added. Only starts actually
    //ticking/rendering once play() calls activate() -- see AchievementToast's
    //class doc.
    private final AchievementToast achievementToast;

    //ROADMAP item 2: ephemeral per-game tracking for FLAWLESS_GAME/
    //COMEBACK_KID -- reset every new game by restartForNewGame(), not
    //persisted (SaveData only stores permanent unlock state).
    private int roundsHitBonusThisGame;
    private boolean wasSoleLastAtHalfway;

    //AI-only: the human's name is captured live via the Start Screen
    //(captureHumanName), not passed in as a list slot -- see the constructor.
    private static final List<String> names = new ArrayList<>() {{
        // add("Player One");
        // add("Player Two");
        // add("Player Three");
        // add("Player Four");
        add("Medium Balanced");
        add("Medium Bold");
        add("Medium Cautious");
        add("Easy");
    }};

    public Game() {
        this(names);
    }

    /**
     * Split out from the constructor so tests can skip popping a real
     * on-screen window (Window's own constructor calls game.start()) --
     * mirrors Window.buildFrame's split for the same reason. Package-
     * private and non-final so a test subclass can override it to a no-op.
     */
    void buildWindow() {
        new Window(WIDTH, HEIGHT, "Ten to One", this);
    }

    /**
     * ROADMAP item 2: new seam, package-private and non-final, mirroring
     * buildWindow()/captureHumanName()'s existing testability pattern -- a
     * test subclass overrides this to point at a temp file instead of the
     * real {@code ~/.tentoone}, so constructing a (Headless)Game in a test
     * never touches the real save file.
     */
    SaveStore buildSaveStore() {
        return new SaveStore();
    }

    /**
     * design/persistent-game-state.md Phase 6/7: new seam, package-private
     * and non-final, mirroring buildSaveStore()'s own testability pattern --
     * a test subclass overrides this to point at a temp file instead of the
     * real {@code ~/.tentoone}, so constructing a (Headless)Game in a test
     * never touches the real game-state save file.
     */
    GameStateStore buildGameStateStore() {
        return new GameStateStore();
    }

    public Game(List<String> aiNames) {
        handler = new Handler();
        mouseInput = new MouseInput();
        keyInput = new KeyInput();
        this.addMouseListener(mouseInput);
        this.addKeyListener(keyInput);
        this.setFocusable(true);

        saveStore = buildSaveStore();
        saveData = saveStore.load();
        gameStateStore = buildGameStateStore();
        achievementToast = new AchievementToast();
        handler.keepOnTop(achievementToast);

        buildWindow();

        //handler.addObject(new Card(Suit.HEARTS, CardValue.ACE));

        this.aiNames = aiNames;
        assert aiNames.size() + 1 <= 5 : "You cannot have more than 5 players";

        players = new ArrayList<>();

        //Code-review fix (ROADMAP item 14): offerResume=true here (this used
        //to be a hardcoded false, via captureHumanName()) -- mirrors
        //handleMenuReturn()'s own runStartScreen() call, so a resumable
        //on-disk snapshot (written continuously during play by
        //saveGameStateCheckpoint(), independent of whether *this* process is
        //the one that wrote it -- see GameStateStore's own class doc) is
        //offered right at real process boot, not just the in-session
        //Menu-return round trip. Neither branch below calls renderPlayers()
        //itself -- play()'s own first-thing renderPlayers() call (unchanged)
        //is what actually registers whichever players end up in this list
        //with the Handler, exactly once; see that method's own comment on
        //the double-registration trap this avoids.
        StartScreenOutcome outcome = captureStartScreenOutcome(aiNames, true);
        if (outcome.resumed) {
            Optional<GameStateSnapshot> maybeSnapshot = gameStateStore.load();
            boolean resumedOk = false;
            if (maybeSnapshot.isPresent()) {
                try {
                    reconstructFromSnapshot(maybeSnapshot.get());
                    resumedOk = true;
                } catch (GameStateReconstructionException e) {
                    e.printStackTrace();
                    gameStateStore.clear();
                }
            }
            if (!resumedOk) {
                //Defensive fallback, mirrors resumeFromSavedGame()'s own --
                //shouldn't happen (Resume is only offered when
                //hasResumableGame() was true moments earlier, inside
                //runStartScreen()) but a since-deleted/corrupt snapshot must
                //not leave this instance half-built. Re-shows the Start
                //Screen (without Resume, since we just established there's
                //nothing usable to resume) to capture a name for a fresh
                //game instead.
                //
                //Bug fix (user-reported, live playthrough): must NOT call
                //establishFreshGameState() here -- that method's own
                //renderPlayers() call, stacked on top of play()'s own
                //unconditional first-thing renderPlayers() call moments
                //later, double-registered every Player with the Handler
                //(exactly the trap this constructor's own comment above 
                //already warned about, but didn't actually avoid). roundIndex/
                //currentRound/roundsHitBonusThisGame/wasSoleLastAtHalfway are
                //already at their correct just-constructed defaults here, so
                //only players actually needs building.
                gameStateStore.clear();
                players.addAll(buildPlayers(aiNames, captureHumanName(aiNames)));
            }
        } else {
            //Code-review fix (ROADMAP item 14): the player was offered
            //Resume and declined it in favor of a fresh New Game -- clear
            //the now-abandoned saved game, same rationale as
            //establishFreshGameState()'s own gameStateStore.clear() call
            //(leaving it on disk would let a later Resume try to reconstruct
            //Player instances that don't match this instance's fresh
            //`players` list).
            //
            //Bug fix (user-reported, live playthrough): same
            //double-registration trap as the branch above -- do not call
            //establishFreshGameState() (its own renderPlayers() call stacks
            //with play()'s), just build the fresh player list directly.
            gameStateStore.clear();
            players.addAll(buildPlayers(aiNames, outcome.humanName));
        }
    }

    /**
     * ROADMAP item 10: the "build a fresh set of players" logic, extracted
     * from the constructor so the Menu-return recovery path (see
     * establishFreshGameState()) can rebuild an equivalent fresh player list
     * on the same live Game instance without duplicating this switch. Always
     * seats the human first (matching every other assumption in this class
     * that getPlayers().get(0) is the human), then one AI per aiNames entry
     * with the same hardcoded personality assignment the constructor always
     * used. Returns a new list rather than mutating the `players` field
     * directly -- callers decide how to apply it (constructor assigns
     * directly; the recovery path clears+addAlls into the existing final
     * field, per its own doc).
     */
    private List<Player> buildPlayers(List<String> aiNames, String humanName) {
        List<Player> newPlayers = new ArrayList<>();
        //design/persistent-game-state.md Phase 7: the checkpoint callback
        //reads Game's own live fields (currentRound et al.) at call time via
        //this method reference -- never a captured stale copy.
        newPlayers.add(new Human(humanName, mouseInput, handler, achievementToast, saveData,
                this::saveGameStateCheckpoint, gameSettings, this::onReturnToMenu, this::onRestartConfirmed));
        for (int i = 0; i < aiNames.size(); i++) {
            String aiName = aiNames.get(i);
            AIPersonality personality;
            switch (i) {
                case 0:
                    personality = AIPersonality.MEDIUM_BALANCED;
                    break;
                case 1:
                    personality = AIPersonality.MEDIUM_BOLD;
                    break;
                case 2:
                    personality = AIPersonality.MEDIUM_CAUTIOUS;
                    break;
                default:
                    personality = null; // For AI_Easy, no personality needed
                    break;
            }
            if (personality != null) {
                newPlayers.add(new AI_Medium(aiName, personality));
            } else {
                newPlayers.add(new AI_Easy(aiName));
            }
        }
        return newPlayers;
    }

    /**
     * design/persistent-game-state.md Phase 8: reconstructs a live, playable
     * Game directly from a saved snapshot -- skips the normal constructor's
     * Start Screen click-loop/captureHumanName/hardcoded AI-personality
     * assignment entirely, since all of that state already exists in
     * SNAPSHOT (via GameStateCodec.fromSnapshot). Not called from main() or
     * the Start Screen this pass -- the hamburger-menu/Start-Screen "Resume
     * Game" UI (ROADMAP item 10/14) that would call this is explicitly out
     * of scope here; exercised directly by tests only.
     *
     * aiNames is approximated from the reconstructed non-human players'
     * names -- the only thing aiNames is used for post-construction is
     * captureHumanName's Start Screen AI-name display list, consulted again
     * by restartForNewGame() after a resumed game's eventual Play Again.
     * This is a reasonable approximation (a snapshot doesn't separately
     * carry the original aiNames list), but what Play-Again-after-a-resumed-
     * game should really do is an open product question, not decided here --
     * flagging for whoever designs the actual Resume UI.
     */
    Game(GameStateSnapshot snapshot) throws GameStateReconstructionException {
        handler = new Handler();
        mouseInput = new MouseInput();
        keyInput = new KeyInput();
        this.addMouseListener(mouseInput);
        this.addKeyListener(keyInput);
        this.setFocusable(true);

        saveStore = buildSaveStore();
        saveData = saveStore.load();
        gameStateStore = buildGameStateStore();
        achievementToast = new AchievementToast();
        handler.keepOnTop(achievementToast);

        buildWindow();

        GameStateCodec.Reconstructed reconstructed = GameStateCodec.fromSnapshot(snapshot, WIDTH, HEIGHT, handler,
                mouseInput, achievementToast, saveData, this::saveGameStateCheckpoint,
                gameSettings, this::onReturnToMenu, this::onRestartConfirmed);

        players = reconstructed.players;
        this.aiNames = players.stream()
                .filter(player -> player.getID() == ID.AI)
                .map(Player::getName)
                .collect(Collectors.toList());

        renderPlayers();

        currentRound = reconstructed.round;
        if (currentRound != null) {
            // Round reconstruction (inside fromSnapshot, above) positioned
            // the human's hand using the player's x/y *before* renderPlayers()
            // (just above) had actually set it -- fix it up now that it has.
            currentRound.repositionHumanHand();
        }

        roundIndex = snapshot.roundIndex;
        roundStartingPlayer = snapshot.roundStartingPlayer;
        roundsHitBonusThisGame = snapshot.roundsHitBonusThisGame;
        wasSoleLastAtHalfway = snapshot.wasSoleLastAtHalfway;
    }

    /**
     * New seam (ROADMAP item 1, backend half), package-private and
     * non-final -- mirrors buildWindow()'s existing testability seam so a
     * test subclass can override it to a canned name instead of blocking on
     * a real click nothing in a test ever delivers. Called right after
     * buildWindow() has made the frame visible, before any Player is
     * constructed.
     */
    String captureHumanName(List<String> aiNames) {
        return runStartScreen(aiNames, false).humanName;
    }

    /**
     * Code-review fix (ROADMAP item 14): the boot-time analog of
     * captureHumanName() above -- same runStartScreen() click-loop seam, but
     * surfaces the full StartScreenOutcome (New Game vs. Resume Game)
     * instead of collapsing it down to a human-name string, since the plain
     * constructor (unlike every other runStartScreen() caller except
     * handleMenuReturn()) needs to branch on which control the user actually
     * clicked. Package-private and non-final, mirroring
     * captureHumanName()'s own testability-seam convention -- the
     * HeadlessGame test subclass overrides this one too, so every existing
     * test that constructs a plain Game(aiNames) keeps getting a canned
     * fresh-name outcome instead of blocking on a real click nothing in a
     * test ever delivers.
     */
    StartScreenOutcome captureStartScreenOutcome(List<String> aiNames, boolean offerResume) {
        return runStartScreen(aiNames, offerResume);
    }

    /**
     * ROADMAP item 10 §6: the outcome of one runStartScreen() call -- either
     * a submitted, non-empty human name (fresh game) or a Resume selection.
     * humanName is null when resumed is true and vice versa.
     *
     * Package-private (not private): ROADMAP item 14's boot-time resume fix
     * needs a test seam (captureStartScreenOutcome(), below) that returns
     * this type, and the HeadlessGame test subclass that overrides it lives
     * in a different top-level file (TestGame.java) -- see that seam's own
     * doc.
     */
    static final class StartScreenOutcome {
        final String humanName;
        final boolean resumed;

        private StartScreenOutcome(String humanName, boolean resumed) {
            this.humanName = humanName;
            this.resumed = resumed;
        }

        static StartScreenOutcome name(String humanName) {
            return new StartScreenOutcome(humanName, false);
        }

        static StartScreenOutcome resume() {
            return new StartScreenOutcome(null, true);
        }
    }

    /**
     * Blocking click-loop for the Start Screen -- used both pre-launch (via
     * captureHumanName, offerResume always false there) and by the "Menu"
     * hamburger item's recovery flow (handleMenuReturn(), offerResume true).
     * Reuses the same StartScreen instance across a Rules/Achievements
     * round-trip (nested view shown on top via its own showBlocking call,
     * then this loop resumes) so a partially-typed name survives visiting
     * either and coming back.
     *
     * ROADMAP item 10 §6: offerResume gates whether the Resume Game button
     * can even show -- hasResumableGame() is only consulted when the caller
     * asks for it, so the original pre-launch/restart call sites (which never
     * have a resumable game at the moment they call this: the constructor
     * runs before any game state exists, and restartForNewGame() clears the
     * saved game before calling captureHumanName) are unaffected either way.
     *
     * ROADMAP item 10 follow-up: this loop now polls (awaitClickOrTimeout)
     * instead of blocking forever on awaitClick(), so it can also notice
     * StartScreen's Enter-to-submit flag between clicks -- see
     * consumeSubmitRequested()'s own doc for why that's a plain poll flag
     * rather than a queue. Enter performs the same action as whichever
     * button is currently showing/eligible (Resume if resumable, else
     * Start) -- both branches reuse attemptStartSubmit() rather than
     * duplicating the START case's own logic.
     *
     * Code-review fix: keyInput.setTarget(startScreen) stays pinned to this
     * StartScreen instance for the whole method, including while a nested
     * Rules/Achievements/Settings/Stats view is shown on top of it via its
     * own showBlocking call below -- so a stray Enter pressed while one of
     * those is open still sets this StartScreen's submitRequested flag (Key
     * Input has no notion of "this target is temporarily backgrounded").
     * Previously nothing drained that flag when the nested view closed, so
     * the very next loop iteration would spuriously fire Resume/Start the
     * instant the user closed Rules/Achievements/Settings/Stats -- each
     * mouseInput.clearClicks() call below is now paired with a
     * consumeSubmitRequested() call for the same reason, discarding
     * whatever it returns.
     *
     * Package-private (not private), mirroring awaitPlayAgain's own
     * testability-seam pattern, so TestGame can drive this directly with
     * injected clicks/keystrokes (see getMouseInput()/getKeyInput()) without
     * going through a HeadlessGame subclass's captureStartScreenOutcome
     * override, which exists specifically to bypass this loop entirely.
     */
    StartScreenOutcome runStartScreen(List<String> aiNames, boolean offerResume) {
        boolean resumable = offerResume && hasResumableGame();
        //ROADMAP item 2: the stat line's data is a snapshot of saveData as of
        //this StartScreen's construction -- fine, since saveData only
        //changes via this same class's round-end/game-end/name-submission
        //hooks, none of which run while a StartScreen is on screen.
        StartScreen startScreen = new StartScreen(saveData.gamesPlayed, resumable, saveData.lastUsedName);
        handler.addObject(startScreen);
        InteractionLog.logShown("StartScreen");
        keyInput.setTarget(startScreen);
        this.requestFocusInWindow();
        try {
            mouseInput.clearClicks();
            while (true) {
                if (startScreen.consumeSubmitRequested()) {
                    InteractionLog.logEvent("KEY Enter -> StartScreen." + (resumable ? "RESUME" : "START"));
                    if (resumable) {
                        return StartScreenOutcome.resume();
                    }
                    StartScreenOutcome outcome = attemptStartSubmit(startScreen);
                    if (outcome != null) {
                        return outcome;
                    }
                    continue;
                }
                Point click = mouseInput.awaitClickOrTimeout(START_SCREEN_SUBMIT_POLL_MILLIS);
                if (click == null) {
                    continue;
                }
                StartScreen.Control control = startScreen.controlAt(click.x, click.y);
                if (control == null) {
                    //ROADMAP item 10 follow-up: a click inside the name field
                    //places the cursor there (standard text-field
                    //click-to-position convention) instead of being just
                    //another unmatched miss-click.
                    if (startScreen.isNameField(click.x, click.y)) {
                        startScreen.clickNameField(click.x);
                        InteractionLog.logClick(click.x, click.y, "StartScreen.NameField");
                    } else {
                        InteractionLog.logClick(click.x, click.y, "no control matched (StartScreen)");
                    }
                    continue;
                }
                InteractionLog.logClick(click.x, click.y, "StartScreen." + control);
                switch (control) {
                    case RULES:
                        RulesView.showBlocking(handler, mouseInput, achievementToast);
                        mouseInput.clearClicks();
                        startScreen.consumeSubmitRequested();
                        continue;
                    case ACHIEVEMENTS:
                        AchievementsView.showBlocking(handler, mouseInput, saveData, achievementToast);
                        mouseInput.clearClicks();
                        startScreen.consumeSubmitRequested();
                        continue;
                    case SETTINGS:
                        SettingsView.showBlocking(handler, mouseInput, achievementToast, gameSettings);
                        mouseInput.clearClicks();
                        startScreen.consumeSubmitRequested();
                        continue;
                    case STATS:
                        StatsView.showBlocking(handler, mouseInput, achievementToast, saveData);
                        mouseInput.clearClicks();
                        startScreen.consumeSubmitRequested();
                        continue;
                    case RESUME:
                        return StartScreenOutcome.resume();
                    case START: {
                        StartScreenOutcome outcome = attemptStartSubmit(startScreen);
                        if (outcome != null) {
                            return outcome;
                        }
                        continue;
                    }
                }
            }
        } finally {
            handler.removeObject(startScreen);
            keyInput.setTarget(null);
        }
    }

    /** ROADMAP item 10 follow-up: poll interval for runStartScreen()'s Enter-to-submit check -- short enough to feel instant, not so short it busy-loops. */
    private static final long START_SCREEN_SUBMIT_POLL_MILLIS = 50;

    /**
     * Attempts to submit STARTSCREEN's currently-typed name -- shared by the
     * START click case and Enter-to-submit (runStartScreen(), above) so
     * neither duplicates the other's logic. Returns null (caller should
     * continue looping) if the trimmed name is empty; otherwise persists
     * lastUsedName (ROADMAP item 10 follow-up: every successful submission,
     * not just a Bapi-easter-egg unlock, now writes to disk) and returns the
     * outcome.
     */
    private StartScreenOutcome attemptStartSubmit(StartScreen startScreen) {
        String typedName = startScreen.getName().trim();
        if (typedName.isEmpty()) {
            return null;
        }
        saveData.lastUsedName = typedName;
        //ROADMAP item 2: the Bapi easter egg, checked at name-submission
        //time -- same permanent-unlock semantics as every other achievement,
        //so re-entering "Bapi" after it's already unlocked is a no-op here
        //(checkNameSubmission returns false) and doesn't re-fire the toast.
        boolean bapiNewlyUnlocked = AchievementEngine.checkNameSubmission(saveData, typedName);
        saveStore.save(saveData);
        if (bapiNewlyUnlocked) {
            achievementToast.enqueue(Achievement.BAPI_EASTER_EGG);
        }
        return StartScreenOutcome.name(typedName);
    }

    private int numCardsThisRound() {
        return 10 - roundIndex;
    }

    /** ROADMAP item 10: whether every player has already placed a bet this round -- see playOneRound()'s mid-betting-phase resume comment. */
    private boolean allPlayersHaveBet() {
        return getPlayers().stream().allMatch(Player::hasBet);
    }

    /**
     * The one-time render setup play() needs before its first frame --
     * extracted so a test can verify it directly without entering play()'s
     * own infinite loop (mirrors this class's existing buildWindow/
     * captureHumanName testability-seam convention).
     *
     * Bug fix (user-reported, live playthrough): a process-boot Resume (the
     * constructor's own reconstructFromSnapshot() branch, reached from java
     * Game's main()/run() -- as opposed to an in-session Menu->Resume, which
     * goes through resumeFromSavedGame() and already calls both of these)
     * deliberately defers renderPlayers()/repositionHumanHand() to here (see
     * reconstructFromSnapshot()'s own doc on why), but only the
     * renderPlayers() half of that was ever actually done here -- the
     * repositionHumanHand() call was missing entirely. Without it, the
     * human's Hand keeps the x/y positionHumanHand() gave it *before*
     * renderPlayers() (just above) set the player's real on-screen position,
     * i.e. stale/default (0,0) -- so Hand.layoutCards()'s
     * `getX() * i / numCards` puts every card at x=0, stacking them exactly
     * on top of each other. Looks exactly like "not all my cards show up in
     * my hand" after a full app close+reopen (every other resume path was
     * unaffected).
     */
    void establishInitialRenderState() {
        renderPlayers();
        if (currentRound != null) {
            currentRound.repositionHumanHand();
        }
    }

    private void play() {
        establishInitialRenderState();
        //ROADMAP item 2: real gameplay begins here -- flips the toast queue
        //live so anything enqueued during the Start Screen (the Bapi easter
        //egg fires at name-submission time, before this point) renders on
        //the first frame of actual play rather than during/underneath the
        //Start Screen or the transition into it. See AchievementToast's
        //class doc.
        achievementToast.activate();
        //ROADMAP item 1 (play-again restart): outer loop runs forever --
        //the only way this process ever exits is the player closing the
        //window (Window.java sets JFrame.EXIT_ON_CLOSE), same as before this
        //change. renderPlayers() above stays a one-time call: it does
        //handler.addObject(player) for every player, so calling it again in
        //here would double-add the same Player instances to the Handler's
        //CopyOnWriteArrayList, silently doubling every per-frame tick()/
        //render() call per player.
        while (true) {
            try {
                //for each round
                while (roundIndex < 10) {
                    playOneRound();
                }
                //determine winner
                Player winner = determineWinner();
                System.out.println(winner.getName() + " won the game!");

                //design/persistent-game-state.md Phase 8: a completed game
                //shouldn't offer resume -- clear the saved game now, before the
                //game-over banner/Play Again flow even starts.
                gameStateStore.clear();

                //ROADMAP item 2: game-end achievement/stat checks -- updates
                //gamesPlayed/gamesWon/highScore/currentWinStreak/bestWinStreakEver
                //and checks FIRST_VICTORY/TEN_GAMES_PLAYED/WIN_STREAK_3/5/10/
                //FLAWLESS_GAME/COMEBACK_KID, all *before* showGameOverBanner so the
                //banner can reflect the just-updated values (new-high-score/
                //streak lines).
                boolean humanWon = isHumanWinner(winner);
                Player human = getHumanPlayer();
                int finalHumanScore = human.getScore();
                int previousHighScore = saveData.highScore;
                int previousWinStreak = saveData.currentWinStreak;
                boolean flawlessGame = isFlawlessGame(roundsHitBonusThisGame);

                List<Achievement> newlyUnlockedThisGame = AchievementEngine.checkGameEnd(
                        saveData, humanWon, finalHumanScore, flawlessGame, wasSoleLastAtHalfway);
                saveStore.save(saveData);
                achievementToast.enqueueAll(newlyUnlockedThisGame);

                boolean newHighScore = finalHumanScore > previousHighScore;
                int streakToReport = humanWon ? saveData.currentWinStreak : previousWinStreak;

                GameOverBanner banner = showGameOverBanner(winner, newHighScore, streakToReport);
                awaitPlayAgain(banner);
                handler.removeObject(banner);
                restartForNewGame();
            } catch (ReturnToMenuSignal signal) {
                //ROADMAP item 10 ("Menu"): the in-progress game was already
                //checkpointed by onReturnToMenu() right before this was
                //thrown -- clean up the stale Player/trump/hand registrations
                //(see cleanupHandlerForMenuOrRestart()'s doc) then hand off to
                //the Resume-aware Start Screen.
                cleanupHandlerForMenuOrRestart();
                handleMenuReturn();
            } catch (RestartGameSignal signal) {
                //ROADMAP item 10 ("Restart", confirmed): abandonAndRestart()
                //does its own handler cleanup (see its doc) before delegating
                //to the unmodified restartForNewGame().
                abandonAndRestart();
            }
        }
    }

    /**
     * ROADMAP item 10 ("Menu"/"Restart"): thrown as ReturnToMenuSignal, an
     * unchecked control-flow signal caught only by play()'s own outer loop --
     * see that class's doc for the full propagation story. Checkpoints once
     * more immediately before throwing (belt-and-suspenders on top of
     * whichever Human-loop checkpoint already ran just before this was
     * invoked) so the saved game reflects the exact moment Menu was clicked.
     */
    private void onReturnToMenu() {
        saveGameStateCheckpoint();
        throw new ReturnToMenuSignal();
    }

    /**
     * ROADMAP item 10 ("Restart", confirmed): thrown as RestartGameSignal --
     * only ever invoked after HamburgerMenu's own "Are you sure? [Yes]/[No]"
     * confirmation step has already resolved to Yes, so by this point the
     * user has clicked Restart twice, not once.
     */
    private void onRestartConfirmed() {
        throw new RestartGameSignal();
    }

    /**
     * ROADMAP item 10 ("Menu"/"Restart"): removes every currently-registered
     * Player from the Handler (required to avoid double-registration once
     * either recovery path re-adds a fresh or reconstructed player list via
     * renderPlayers() -- otherwise every player's tick()/render() would fire
     * twice a frame), plus the in-progress round's trump card and the
     * human's Hand object, if either exists. Package-private (not private),
     * mirroring this class's existing testability-seam convention, so
     * TestGame can exercise this cleanup step directly.
     *
     * Bug fix (user-reported, live playthrough, 2026-07-07): also strips
     * every still-registered Trick-played Card object -- previously left
     * alone here on the strength of this method's own now-stale comment
     * ("a known, pre-existing, accepted leak, ROADMAP item 16"), which
     * turned out to describe only the trump-card/hand half of that item
     * ("practically negligible, no visible symptom" per its own writeup),
     * not this half. Trick.play() registers each played Card directly with
     * the Handler and only ever removes them via Round.playRound()'s
     * handler.removeAll(cardsPlayed), reached once currentTrick.play()
     * returns *normally* -- but Menu/Restart confirmed from inside a
     * blocking Human.playCard() click-loop mid-trick throws
     * ReturnToMenuSignal/RestartGameSignal straight out of that loop,
     * unwinding past playRound()'s post-trick cleanup entirely. Left
     * unfixed, any seat that had already played this trick stays registered
     * with the Handler forever, rendering on top of every later screen --
     * including, if it happened to be this trick's running-highest card,
     * its gold high-card ring. The trump card and every Trick-played card
     * are the only two kinds of bare Card ever independently registered
     * with this Handler (a Hand's own cards are never registered
     * individually -- see Hand.render(), which iterates its own internal
     * list), so removing every still-registered Card here is exactly the
     * right scope -- this folds the previously-separate explicit trump-card
     * removal into the same pass.
     */
    void cleanupHandlerForMenuOrRestart() {
        for (Player player : getPlayers()) {
            handler.removeObject(player);
        }
        handler.removeAllOfType(Card.class);
        getPlayers().stream()
                .filter(player -> player.getID() == ID.HUMAN)
                .findFirst()
                .map(Player::getHand)
                .ifPresent(handler::removeObject);
    }

    /**
     * ROADMAP item 10 ("Menu"): re-runs the Start Screen (with the Resume
     * button available, since a checkpoint was just written by
     * onReturnToMenu() right before this was reached) and branches on
     * whether the player picks Resume or types a name and clicks New Game.
     *
     * Known, accepted, explicitly-not-decided-here product gap: if the
     * player picks a fresh New Game instead of Resume, the suspended game
     * is abandoned with no extra confirmation (unlike Restart, which requires
     * an explicit second click) -- see this item's completion report.
     */
    void handleMenuReturn() {
        StartScreenOutcome outcome = runStartScreen(aiNames, true);
        if (outcome.resumed) {
            resumeFromSavedGame();
        } else {
            establishFreshGameState(outcome.humanName);
        }
    }

    /**
     * ROADMAP item 10 §6: reconstructs live state from the on-disk snapshot
     * directly onto this same Game instance -- reusing the already-built
     * handler/mouseInput/achievementToast/saveData (and this Window), NOT
     * constructing a second Game/Window the way the Game(GameStateSnapshot)
     * constructor does for a genuine fresh-process relaunch. Mirrors that
     * constructor's own body, minus the window/handler/mouseInput
     * construction it doesn't need to repeat here.
     *
     * A missing or corrupt snapshot (shouldn't happen -- Resume is only
     * offered when hasResumableGame() was true at Start Screen construction
     * time -- but defensively handled rather than assumed) falls back to a
     * fresh game rather than leaving the instance in a half-built state,
     * mirroring SaveStore/GameStateStore's own never-crash-gameplay
     * convention. Package-private (not private), mirroring this class's
     * existing testability-seam convention, so TestGame can exercise the
     * reconstruction step directly without threading a click through the
     * full Start Screen Resume button.
     */
    void resumeFromSavedGame() {
        Optional<GameStateSnapshot> maybeSnapshot = gameStateStore.load();
        if (maybeSnapshot.isEmpty()) {
            establishFreshGameState(captureHumanName(aiNames));
            return;
        }
        try {
            reconstructFromSnapshot(maybeSnapshot.get());

            renderPlayers();
            if (currentRound != null) {
                // Round reconstruction (inside fromSnapshot, above) positioned
                // the human's hand using the player's x/y *before* renderPlayers()
                // (just above) had actually set it -- fix it up now that it has.
                // Same order-of-operations the Game(GameStateSnapshot) constructor
                // already has to work around -- see its own comment.
                currentRound.repositionHumanHand();
            }
        } catch (GameStateReconstructionException e) {
            e.printStackTrace();
            gameStateStore.clear();
            establishFreshGameState(captureHumanName(aiNames));
        }
    }

    /**
     * Code-review fix (ROADMAP item 14): the "load a snapshot's object graph
     * onto this Game instance's own fields" core, extracted from
     * resumeFromSavedGame() so the plain (boot-time) constructor's own Resume
     * branch can reuse it too, instead of a third inline copy of this same
     * GameStateCodec.fromSnapshot() call + field-population sequence.
     * Deliberately does NOT call renderPlayers()/repositionHumanHand() --
     * callers differ on when it's safe to do that. resumeFromSavedGame()
     * calls both immediately after, since play()'s own one-time
     * renderPlayers() call already ran long before an in-session Menu click
     * is possible; the constructor defers both to play()'s own upcoming
     * first-thing renderPlayers() call instead (see play()'s own comment),
     * to avoid double-registering every Player with the Handler.
     */
    private void reconstructFromSnapshot(GameStateSnapshot snapshot) throws GameStateReconstructionException {
        GameStateCodec.Reconstructed reconstructed = GameStateCodec.fromSnapshot(snapshot, WIDTH, HEIGHT,
                handler, mouseInput, achievementToast, saveData, this::saveGameStateCheckpoint,
                gameSettings, this::onReturnToMenu, this::onRestartConfirmed);

        players.clear();
        players.addAll(reconstructed.players);
        currentRound = reconstructed.round;
        roundIndex = snapshot.roundIndex;
        roundStartingPlayer = snapshot.roundStartingPlayer;
        roundsHitBonusThisGame = snapshot.roundsHitBonusThisGame;
        wasSoleLastAtHalfway = snapshot.wasSoleLastAtHalfway;
    }

    /**
     * ROADMAP items 10/14: rebuilds a brand-new set of players (via
     * buildPlayers(), the same logic the constructor itself uses) directly
     * onto this same Game instance, and resets round bookkeeping to a fresh
     * game's starting state -- mirrors restartForNewGame()'s own reset
     * fields, but on freshly-built Player objects rather than the same
     * instances. Shared by two callers: the "Menu" hamburger item's
     * fresh-Start-Game branch (old Player instances already removed from the
     * handler by cleanupHandlerForMenuOrRestart()), and the boot-time
     * constructor's own declined-Resume/missing-snapshot branches (no old
     * Player instances exist yet in that case -- players is still empty).
     *
     * Clears the now-abandoned saved game: leaving it on disk would let a
     * later Resume (e.g. after a crash) try to resume state describing
     * Player objects that no longer match this instance's live `players`
     * list -- a latent, worse bug than simply losing the abandoned game.
     *
     * Package-private (not private), mirroring resumeFromSavedGame()'s own
     * testability-seam convention, so TestGame can exercise the Menu-return
     * fresh-game path's Handler re-registration directly without threading
     * real clicks through the full Start Screen.
     */
    void establishFreshGameState(String humanName) {
        //ROADMAP item 10 follow-up: see restartForNewGame()'s identical call
        //for why this belongs here too -- both are "a genuinely fresh game is
        //starting" moments (see GameSettings' own class doc).
        gameSettings.applyPending();
        gameStateStore.clear();
        players.clear();
        players.addAll(buildPlayers(aiNames, humanName));
        currentRound = null;
        roundIndex = 0;
        roundStartingPlayer = new Random().nextInt(numPlayers());
        roundsHitBonusThisGame = 0;
        wasSoleLastAtHalfway = false;
        renderPlayers();
    }

    /**
     * ROADMAP item 10 ("Restart", confirmed): NOT simply restartForNewGame().
     * Resetting currentWinStreak to 0 (and persisting it immediately) is a
     * distinct preceding step for an abandoned game, not a variant of
     * restart itself -- this deliberately does NOT run
     * AchievementEngine.checkGameEnd or touch gamesPlayed/gamesWon/highScore
     * (this game never finished, it was abandoned), and does NOT modify
     * restartForNewGame()'s own signature/behavior, which the natural
     * post-game-end Play Again flow still uses unchanged.
     *
     * Bug found via manual verification of this item (not caught by the
     * automated suite, which never drove a full Restart-mid-round -> next
     * round sequence): restartForNewGame() resets every player's state
     * (including nulling each player's Hand, via Player.resetForNewGame())
     * but was never responsible for nulling Game's own currentRound field --
     * before this item, restartForNewGame() was only ever reachable once a
     * round had already finished normally (currentRound already null by
     * then). Restart confirmed mid-round is this item's new way to reach
     * restartForNewGame() while currentRound is still genuinely non-null; if
     * left as-is, playOneRound()'s resume-aware currentRound == null check
     * would treat that stale Round as still in progress and call bet() on
     * players whose hands had just been reset to null -- an NPE. Nulling it
     * here, before restartForNewGame() runs, closes that gap without
     * touching restartForNewGame() itself.
     *
     * Second bug found the same manual pass, same root cause shape:
     * cleanupHandlerForMenuOrRestart() removes every player from the Handler
     * (required for the Menu/Resume path, where a *new* set of
     * reconstructed/rebuilt Player objects gets re-registered via
     * renderPlayers() afterward) -- but restartForNewGame() reuses the exact
     * same Player instances and, by its own established contract, never
     * re-adds them to the Handler itself (before this item, restartForNewGame()
     * was only ever reachable via the natural Play-Again flow, which never
     * removes players from the Handler in the first place, so nothing needed
     * to re-add them). Left unfixed, a mid-round Restart leaves every
     * player -- human included -- permanently deregistered: still fully
     * functional as game-logic objects (Round/Trick call their methods
     * directly, not through the Handler), but invisible on screen forever
     * after (confirmed live: no HUD text, no name, no score, for any seat,
     * for the rest of the session). renderPlayers() re-adds these same
     * instances after restartForNewGame() runs.
     */
    void abandonAndRestart() {
        saveData.currentWinStreak = 0;
        saveStore.save(saveData);
        gameStateStore.clear();
        cleanupHandlerForMenuOrRestart();
        currentRound = null;
        restartForNewGame();
        renderPlayers();
    }

    /**
     * Extracted from play()'s per-round loop body -- package-private test
     * seam (mirrors buildWindow/captureHumanName's existing testability-seam
     * convention) so a test can drive exactly one real round boundary
     * directly, without scripting clicks through play()'s whole forever-loop
     * (bet, betting, all ten rounds, game-over banner, Play Again, etc.).
     * Deals and plays one Round to completion, applies its results, then
     * shows the round-summary click-to-continue gate before advancing
     * roundStartingPlayer/roundIndex for the next call.
     */
    void playOneRound() {
        int currentPlayer = roundStartingPlayer;
        //ROADMAP item 10 §8: currentRound is already non-null exactly when a
        //Resume (see resumeFromSavedGame()) populated it -- skip dealing
        //entirely in that case (deal already happened before the snapshot
        //was taken). currentRound is nulled again below, right after
        //round.playRound() finishes, at the normal point -- so this check is
        //false on every ordinary (non-resumed) call.
        if (currentRound == null) {
            currentRound = new Round(numCardsThisRound(), getPlayers(), currentPlayer, WIDTH, HEIGHT, handler);
            currentRound.bet(currentPlayer, gameSettings);
        } else if (!allPlayersHaveBet()) {
            //Discovered during manual verification of this item, not called
            //out by the original design doc's checkpoint-cadence writeup:
            //design/persistent-game-state.md §6 checkpoints right before/
            //after Human.bet()'s own blocking click-loop -- so a Menu click
            //taken at exactly that moment (e.g. the human is the round's
            //first bettor and hasn't bet yet) resumes with currentRound
            //already non-null but betting genuinely incomplete. Round.bet()
            //itself unconditionally resets every player's bet at its own
            //top, so calling it again necessarily restarts the whole
            //betting phase from the round's original starting player (not
            //just the specific bettor who was mid-turn) -- a deliberate,
            //documented simplification rather than deeper surgery on
            //Round.bet() to resume a partial bet-by-bet sequence. Every
            //other resume case (between rounds, mid-trick, between tricks)
            //already has every player's bet restored (hasBet() true for
            //all), so this branch is a no-op for those and only fires for
            //the specific mid-betting-phase checkpoint case.
            currentRound.bet(currentRound.getCurrentPlayer(), gameSettings);
        }
        Round round = currentRound;

        //play round
        round.playRound();

        //handler.removeAll();

        //Code-review fix (design/persistent-game-state.md): the round has
        //now actually finished (empty hands, reset trick scores once
        //adjustScores() below runs) -- null this out now, before
        //showRoundSummary()'s two checkpoints below, so they correctly
        //report "between rounds" (round == null) rather than a snapshot of
        //the round that just ended. See the field's own doc.
        currentRound = null;

        //snapshot bet/tricksTaken before adjustScores() resets each
        //player's trickScore to 0 -- see RoundResultRow's class doc
        List<RoundResultRow> results = snapshotRoundResults(getPlayers(), roundBonus);

        //adjust scores accordingly
        adjustScores();
        printScores();
        applyTotals(results, getPlayers());

        //ROADMAP item 2: round-end achievement checks -- SCORE_OVER_50/
        //_100 against the human's running score, PERFECT_ROUND for this
        //round's bonus hit, plus the two pieces of ephemeral per-game
        //state FLAWLESS_GAME/COMEBACK_KID need at game-end. Written to
        //disk immediately after (not deferred to game-end), matching
        //this feature's "no clean-shutdown hook" design.
        RoundResultRow humanRow = results.stream().filter(row -> row.isHuman).findFirst()
                .orElseThrow(() -> new IllegalStateException("No human row in round results"));
        List<Achievement> newlyUnlockedThisRound =
                AchievementEngine.checkRoundEnd(saveData, humanRow.totalAfter, humanRow.bonusHit);
        if (humanRow.bonusHit) {
            roundsHitBonusThisGame++;
        }
        //Round-5 halfway snapshot: roundIndex is still 4 here (it
        //increments below, after this block), i.e. this is right
        //after the round where roundIndex was 4 -- the 5th of 10.
        if (roundIndex == 4) {
            List<Integer> otherScores = results.stream()
                    .filter(row -> !row.isHuman)
                    .map(row -> row.totalAfter)
                    .collect(Collectors.toList());
            wasSoleLastAtHalfway = AchievementEngine.isSoleLastPlace(humanRow.totalAfter, otherScores);
        }
        saveStore.save(saveData);
        achievementToast.enqueueAll(newlyUnlockedThisRound);

        showRoundSummary(roundIndex, results);

        roundStartingPlayer = nextPlayer(roundStartingPlayer);
        this.roundIndex++;
    }

    /**
     * ROADMAP item 1: blocks until the "Play Again" hotspot is clicked, same
     * exact shape as RulesView.showBlocking's click loop. Any other click
     * (e.g. a miss-click elsewhere on the frozen banner) is silently
     * ignored -- matches every other hotspot's convention in this codebase,
     * no error feedback on a miss-click.
     *
     * Code-review Finding 1: also checks ACHIEVEMENTTOAST's click-to-dismiss
     * hotspot ahead of the Play Again check, same as RulesView/
     * AchievementsView's showBlocking loops -- this is exactly the screen
     * (game-end streak/first-victory unlocks are enqueued right before the
     * banner shows) the feature most needed to be dismissible on, per that
     * finding. Package-private (not private), mirroring
     * buildWindow/captureHumanName's existing testability-seam pattern, so
     * TestGame can drive this directly -- see getMouseInput/
     * getAchievementToast below.
     */
    void awaitPlayAgain(GameOverBanner banner) {
        mouseInput.clearClicks();
        while (true) {
            Point click = mouseInput.awaitClick();
            if (achievementToast.isToastHotspot(click.x, click.y)) {
                achievementToast.dismiss();
                continue;
            }
            if (banner.isPlayAgainHotspot(click.x, click.y)) {
                return;
            }
        }
    }

    /** Test-only accessor (package-private) -- see awaitPlayAgain's dismiss-wiring test in TestGame. */
    MouseInput getMouseInput() {
        return mouseInput;
    }

    /** Test-only accessor (package-private), mirroring getMouseInput() above -- not otherwise needed by any shipped test in this pass; added to let a one-off manual verification harness drive real name entry without depending on real OS window focus. */
    KeyInput getKeyInput() {
        return keyInput;
    }

    /** Test-only accessor (package-private) -- see awaitPlayAgain's dismiss-wiring test in TestGame. */
    AchievementToast getAchievementToast() {
        return achievementToast;
    }

    /** Test-only accessor (package-private), mirroring getMouseInput()/getAchievementToast() -- lets TestGame confirm no duplicate Player registration after a Menu/Resume round-trip. */
    Handler getHandler() {
        return handler;
    }

    /** Test-only accessor (package-private), mirroring getMouseInput()/getAchievementToast() -- lets TestGame set up/inspect win-streak and achievement-unlock state around abandonAndRestart(). */
    SaveData getSaveData() {
        return saveData;
    }

    /** Test-only accessor (package-private), mirroring getMouseInput()/getAchievementToast() -- lets TestGame confirm SettingsView mutates the exact same GameSettings instance Round.bet() reads from. */
    GameSettings getGameSettings() {
        return gameSettings;
    }

    /** design/persistent-game-state.md Phase 4: the round currently in progress, or null before the first round is constructed. */
    Round getCurrentRound() {
        return currentRound;
    }

    /** Test-only setter (package-private) -- lets TestGame set up a genuinely in-progress currentRound without driving a real blocking bet()/playCard() click sequence. */
    void setCurrentRoundForTest(Round round) {
        currentRound = round;
    }

    /** Test-only accessor (package-private) -- lets TestGame write a specific GameStateSnapshot directly to the same store resumeFromSavedGame() reads from, without driving a real Menu click sequence first. */
    GameStateStore getGameStateStore() {
        return gameStateStore;
    }

    /**
     * design/persistent-game-state.md Phase 7: assembles a GameStateSnapshot
     * from Game's own live fields (read at call time, never a stale capture --
     * see the constructor's comment on the Human callback) and writes it via
     * gameStateStore. Invoked from Human's checkpoint callback (bet/playCard/
     * nextTrick's blocking loops) and directly around showRoundSummary()'s
     * own click-to-continue gate. Never throws out to the caller -- a failed
     * checkpoint save must not interrupt gameplay, matching
     * SaveStore/GameStateStore's own never-crash-gameplay convention (each
     * already catches its own IOExceptions; this catches anything else that
     * could conceivably escape the snapshot-assembly step itself).
     */
    void saveGameStateCheckpoint() {
        try {
            GameStateSnapshot snapshot = GameStateCodec.toSnapshot(roundIndex, roundStartingPlayer,
                    roundsHitBonusThisGame, wasSoleLastAtHalfway, getPlayers(), currentRound);
            gameStateStore.save(snapshot);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /** design/persistent-game-state.md Phase 8: whether a resumable saved game exists on disk. */
    public boolean hasResumableGame() {
        return gameStateStore.exists();
    }

    /**
     * ROADMAP item 1: resets every player's per-game state on the same
     * Player instances (never re-adds them to handler -- see play()'s
     * comment on the double-add trap), resets round bookkeeping, and sends
     * the player back through the Start Screen (the user's explicit
     * decision -- Play Again does not skip straight into a fresh game).
     * Package-private and non-final, mirroring buildWindow/captureHumanName's
     * existing testability seam, so a test can exercise this directly
     * without driving play()'s full click-driven loop.
     */
    void restartForNewGame() {
        //ROADMAP item 10 follow-up: apply any Settings changes staged since
        //the last game started -- this is one of the two places (along with
        //establishFreshGameState()) Game.java establishes a genuinely fresh
        //game's starting state, so this is where a pending toggle actually
        //takes effect. See GameSettings' own class doc for why this can't
        //just be applied live at toggle time.
        gameSettings.applyPending();
        //design/persistent-game-state.md Phase 8: the one other real
        //explicit-restart code path in this codebase -- cheap/idempotent
        //even though play()'s own game-end clear (right before this method's
        //normal call site) has already run; matters if some future path ever
        //restarts without going through the normal game-end flow.
        gameStateStore.clear();
        for (Player player : getPlayers()) {
            player.resetForNewGame();
        }
        roundIndex = 0;
        //re-randomize rather than carry over wherever roundStartingPlayer
        //drifted to after 10 rounds of nextPlayer() cycling -- matches the
        //constructor's original logic exactly, confirmed by game-designer as
        //the fairer, more legible choice.
        roundStartingPlayer = new Random().nextInt(numPlayers());

        //ROADMAP item 2: fresh per-game achievement tracking for the new game
        //-- these are ephemeral (not persisted), so a new game must start
        //clean rather than carrying over the just-finished game's counts.
        roundsHitBonusThisGame = 0;
        wasSoleLastAtHalfway = false;

        String humanName = captureHumanName(aiNames);
        getPlayers().stream()
                .filter(player -> player.getID() == ID.HUMAN)
                .findFirst()
                .ifPresent(human -> human.setName(humanName));
    }

    /** Test-only accessor (package-private) -- see restartForNewGame()'s tests in TestGame. */
    int getRoundStartingPlayer() {
        return roundStartingPlayer;
    }

    /** Test-only accessor (package-private) -- see restartForNewGame()'s tests in TestGame. */
    int getRoundIndex() {
        return roundIndex;
    }

    public void adjustScores() {
        for (Player player : getPlayers()) {
            if (player.getBet() == player.getTrickScore()) {
                player.increaseScore(player.getTrickScore() + roundBonus);
            } else {
                player.increaseScore(player.getTrickScore());
            }
            player.resetTrickScore();
        }
    }

    public void printScores() {
        for (Player player : getPlayers()) {
            System.out.println(player.getName() + " has " + player.getScore() + " points.");
        }
    }

    /**
     * ROADMAP item 2: FLAWLESS_GAME's real boundary -- true only if every
     * one of the game's 10 rounds hit the bonus, not e.g. 9 of 10.
     * roundsHitBonusThisGame is accumulated round-by-round in play()'s loop
     * (incremented once per round whose bonus was hit) and this is checked
     * once at game-end. Pulled out as its own package-private + static
     * method, mirroring snapshotRoundResults/applyTotals's existing
     * testability-seam pattern, so this exact "all 10, not almost-all"
     * threshold is unit-testable without driving play()'s full
     * click-scripted 10-round loop.
     */
    static boolean isFlawlessGame(int roundsHitBonusThisGame) {
        return roundsHitBonusThisGame == 10;
    }

    /**
     * Captures each player's name/isHuman/bet/tricksTaken before
     * adjustScores() resets trickScore to 0. Package-private + static so
     * TestGame can exercise it directly against a scripted bet/trickScore
     * setup without depending on the rest of play()'s flow.
     */
    static List<RoundResultRow> snapshotRoundResults(List<Player> players, int roundBonus) {
        List<RoundResultRow> rows = new ArrayList<>();
        for (Player player : players) {
            rows.add(new RoundResultRow(player.getName(), player.getID() == ID.HUMAN,
                    player.getBet(), player.getTrickScore(), roundBonus));
        }
        return rows;
    }

    /**
     * Fills in each row's totalAfter from players' current score, in the
     * same order snapshotRoundResults() produced rows -- must be called
     * after adjustScores() has actually run.
     */
    static void applyTotals(List<RoundResultRow> rows, List<Player> players) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).totalAfter = players.get(i).getScore();
        }
    }

    /**
     * Adds the round-summary modal (ROADMAP item 1a) and blocks until the
     * player clicks anywhere to dismiss it, same click-anywhere convention
     * Human.nextTrick() already uses. Removed in a finally, mirroring
     * BetStepper's add-before/remove-after lifecycle.
     */
    private void showRoundSummary(int roundIndex, List<RoundResultRow> results) {
        RoundSummaryPanel panel = new RoundSummaryPanel(roundIndex, results);
        // design/persistent-game-state.md Phase 7: right before this
        // click-to-continue gate shows -- Game already has everything it
        // needs directly, no Human-callback plumbing required here.
        saveGameStateCheckpoint();
        handler.addObject(panel);
        try {
            mouseInput.clearClicks();
            mouseInput.awaitClick();
        } finally {
            handler.removeObject(panel);
        }
        // design/persistent-game-state.md Phase 7: right after the click resolves.
        saveGameStateCheckpoint();
    }

    /**
     * Adds the end-of-game outcome banner (ROADMAP item 1c). Since ROADMAP
     * item 1's play-again restart, this is no longer forever -- the banner
     * is removed once the player clicks Play Again (see play()'s
     * awaitPlayAgain/removeObject/restartForNewGame sequence); the return
     * value lets the caller pass this exact instance to awaitPlayAgain and
     * then hand it back to handler.removeObject().
     */
    private GameOverBanner showGameOverBanner(Player winner, boolean newHighScore, int streakToReport) {
        boolean humanWon = isHumanWinner(winner);
        //stable sort (List.sort/TimSort) so ties keep seat order, not an
        //arbitrary reordering
        List<Player> standings = new ArrayList<>(getPlayers());
        standings.sort(Comparator.comparingInt(Player::getScore).reversed());
        GameOverBanner banner = new GameOverBanner(winner, humanWon, standings, newHighScore, streakToReport);
        handler.addObject(banner);
        return banner;
    }

    public Player determineWinner() {
        Player winner = getPlayer(0);
        for (Player player : players) {
            if (player.getScore() > winner.getScore()) {
                winner = player;
            }
        }
        return winner;
    }

    /**
     * ROADMAP item 2: shared by play()'s game-end achievement checks and
     * showGameOverBanner (which used to compute this same thing inline) --
     * true if the given winner is the human seat, not an AI.
     */
    private boolean isHumanWinner(Player winner) {
        return getPlayers().stream()
                .filter(p -> p.getID() == ID.HUMAN)
                .findFirst()
                .map(human -> human == winner)
                .orElse(false);
    }

    /** ROADMAP item 2: the human player is always seated first (see the constructor), but looked up defensively rather than assuming index 0. */
    private Player getHumanPlayer() {
        return getPlayers().stream()
                .filter(p -> p.getID() == ID.HUMAN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No human player found"));
    }

    //Improve
    public Player getPlayer(int i) {
        assert 0 <= i && i < players.size() : i + " is not a valid player";
        //add other boundary as well
        return players.get(i);
    }

    public List<Player> getPlayers() {
        return players;
    }

    private int numPlayers() {
        return players.size();
    }

    private int nextPlayer(int curr) {
        return (curr + 1) % numPlayers();
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        thread = new Thread(this);
        thread.start();
        running = true;
    }

    /**
     * Not synchronized: run() itself calls stop() (on the game thread) right
     * after its own while(running) loop exits. If stop() were a synchronized
     * instance method, an external caller blocked here inside thread.join()
     * would still be holding this instance's monitor, and the game thread's
     * own self-invoked stop() call would block forever trying to enter that
     * same synchronized method -- a deadlock distinct from (and in addition
     * to) the self-join case below. Neither running nor thread needs the
     * monitor for correctness here: running is volatile (see field comment)
     * and thread is only ever written by start().
     */
    public void stop() {
        //flip this unconditionally, and before the join below, so run()'s
        //while(running) loop -- possibly still spinning on another thread
        //right now -- can observe it and exit. The old code set this only
        //after thread.join() returned, so a stop() call from any thread
        //other than `thread` itself would block forever: nothing would ever
        //flip running to let the loop that join() is waiting on finish.
        running = false;
        try {
            //a thread can't join itself: run() reaches this same stop() call
            //on `thread` once its own loop exits, so skip the join in that
            //case -- there's nothing left to wait for, run() is already
            //returning right after this call.
            if (thread != null && Thread.currentThread() != thread) {
                thread.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        long lastTime = System.nanoTime();
        double amountOfTicks = 60.0;
        double ns = 1000000000 / amountOfTicks;
        double delta = 0;
        long timer = System.currentTimeMillis();
        int frames = 0;
        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;
            while (delta >= 1) {
                tick();
                delta--;
            }
            if (running) {
                //a failed frame must not kill the render thread for the rest of the session
                try {
                    render();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            frames++;
            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                //System.out.println("FPS: " + frames);
                frames = 0;
            }
        }
        stop();
    }

    /**
     * ROADMAP item 10 (user feedback pass): the AI seat row's shared render
     * y -- was a 50 literal, moved down to 70 so the hamburger icon/dropdown
     * (now anchored at the very top of the canvas, per user request; see
     * BetStepper.HAMBURGER_TOP's own doc) has room above this row's name
     * text without colliding with it, at every supported player count (the
     * leftmost AI seat is always at x=0, see renderPlayers()'s x formula
     * below, so it's always directly under the top-left-anchored dropdown
     * regardless of how many AI opponents are seated).
     *
     * 70, not something larger: this is a *tight* upper bound, not a
     * comfortable round number picked for its own sake. Pushing this row
     * down further runs into two fixed constraints below it that this change
     * does not touch: NextTrickPrompt/IllegalPlayFeedback's shared centered-
     * message band (baseline y=280) and, below that, the trump card's top
     * border edge (y=305, Round.renderTrumpCard()) -- the two of those are
     * already only ~21px apart with no slack to redistribute. At y=70, this
     * row's own lowest text (the score line, this row's y+185) sits ~9px
     * above the message band; its played card (this row's y+30..+130, up to
     * +14 more for the high-card ring) sits ~91px above the message band and
     * ~30px (with the ring) above the trump card's own top edge (comfortable
     * margin, not the binding constraint). See
     * TestHamburgerIconGeometry for the full algebraic proof, across every
     * supported player count (1-4 AI opponents), that this value clears the
     * hamburger dropdown above and the message band/trump card below.
     */
    static final int AI_ROW_Y = 70;

    public void renderPlayers() {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player.getID() == ID.AI) {
                int x = (WIDTH * (i - 1)) / (numPlayers() - 1);
                int y = AI_ROW_Y;
                player.setX(x);
                player.setY(y);
                handler.addObject(player);
            }
            else if (player.getID() == ID.HUMAN) {
                int x = WIDTH;
                int y = HEIGHT - 150;
                player.setX(x);
                player.setY(y);
                handler.addObject(player);
            }
        }
    }

    private void tick() {
        handler.tick();
    }

    private void render() {
        BufferStrategy bs = this.getBufferStrategy();
        if (bs == null) {
            this.createBufferStrategy(3);
            return;
        }

        Graphics g  = bs.getDrawGraphics();

        g.setColor(Color.white);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        handler.render(g);

        g.dispose();
        bs.show();
    }


    public static void main(String[] args) {
        // InteractionLog: log the session boundary as early/cleanly as
        // possible -- before any Game/Window construction, right at the real
        // process entry point (see InteractionLog's own doc). The matching
        // "shutting down" line is a JVM shutdown hook, not a call at the
        // bottom of this method -- this process never actually returns from
        // game.play() below (Window.java deliberately sets
        // JFrame.EXIT_ON_CLOSE, an accepted design choice this change does
        // not touch), so a shutdown hook is the only way to observe the
        // window-close moment at all. Registered here (not inside Window/
        // Game) so it fires for exactly one real boot, not once per test
        // Game/Window construction. ENABLED defaults to false (see
        // InteractionLog's own doc) so JUnit's direct construction of UI
        // classes stays inert; flip it on here, right at the one real
        // process entry point, before the first log call.
        InteractionLog.ENABLED = true;
        InteractionLog.logEvent("booting up");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> InteractionLog.logEvent("shutting down")));
        Game game = new Game(names);
        game.play();
    }
}
