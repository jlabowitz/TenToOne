/**
 * ROADMAP item 10 (hamburger menu, "Menu" item): an unchecked control-flow
 * signal, not a real error. Thrown by Game's onReturnToMenu callback (wired
 * into Human's three blocking click-loops via handleHamburgerMenu()) and
 * caught nowhere except Game.play()'s own outer loop -- everything in
 * between (Human.bet/playCard/nextTrick, Trick.play(), Round.playRound(),
 * Game.playOneRound()) lets it propagate unmodified; none of those methods
 * catch generic RuntimeException, so this passes straight through their
 * try/finally blocks (still running any finally cleanup along the way,
 * notably Human's own handler.removeObject(feedback/stepper/prompt) calls).
 * See design/persistent-game-state.md and Game.play()'s catch block for the
 * full unwind story.
 */
public class ReturnToMenuSignal extends RuntimeException {
}
