/**
 * ROADMAP item 1: a live-updating text buffer that keyboard input can be
 * routed into. First-ever keyboard-input consumer in this codebase (see
 * KeyInput's class doc) -- StartScreen is the initial implementer, feeding
 * the name-entry field.
 *
 * ROADMAP item 10 follow-up: three more operations, all sourced from
 * KeyInput.keyPressed (modifier-aware, unlike typeChar/backspace which come
 * from keyTyped's plain character stream -- see that class's doc):
 * selectAll()/deleteWord() for Ctrl+A/Ctrl+Backspace, and submit() for Enter.
 *
 * Second follow-up (user feedback: select-all with no visible highlight was
 * confusing, and arrow keys/click-to-position didn't work at all): two more
 * operations, moveCursorLeft()/moveCursorRight() for the Left/Right arrow
 * keys. StartScreen is still the only implementer -- see its own doc for how
 * a single plain String buffer plus a cursor index models a real (if
 * simplified) cursor/selection, including how a click routes to cursor
 * placement (StartScreen.clickNameField(), not on this interface -- Game
 * already holds the concrete StartScreen reference directly, same seam as
 * consumeSubmitRequested()).
 *
 * Third follow-up: moveWordLeft()/moveWordRight() for Ctrl+Left/Ctrl+Right,
 * matching the standard OS convention of jumping a whole word at a time
 * instead of one character.
 */
public interface TypingTarget {
    void typeChar(char c);
    void backspace();
    void selectAll();
    void deleteWord();
    void submit();
    void moveCursorLeft();
    void moveCursorRight();
    void moveWordLeft();
    void moveWordRight();
}
