/**
 * ROADMAP item 10 (hamburger menu, "Restart" item, confirmed): an unchecked
 * control-flow signal, not a real error -- mirrors ReturnToMenuSignal exactly
 * (see its own doc for the propagation story). Thrown only by Game's
 * onRestartConfirmed callback, itself only invoked by HamburgerMenu after its
 * own "Are you sure? [Yes]/[No]" confirmation step has already resolved to
 * Yes -- so by the time this is thrown, the user has already clicked Restart
 * twice, not once. Caught by Game.play()'s own outer loop, which calls
 * abandonAndRestart() -- see that method's doc for why this is not simply
 * restartForNewGame().
 */
public class RestartGameSignal extends RuntimeException {
}
