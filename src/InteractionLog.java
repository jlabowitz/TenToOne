import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Dev-only diagnostic (no effect on gameplay itself): an append-only,
 * plain-text timeline of clicks -- each one's raw canvas x/y plus whichever
 * control/hotspot it actually resolved to (or "no control matched" for a
 * genuine miss-click) -- interleaved with the views/messages the game showed
 * in response (IllegalPlayFeedback's legality message, NextTrickPrompt,
 * AchievementToast, the hamburger menu and every view it opens, the Start
 * Screen), plus session boundaries (process boot/shutdown). Written so a
 * future bug report (e.g. the "Restart Game leaves stale trick-played cards
 * on screen" bug this feature exists to make reproducible) can be replayed
 * exactly from a log file instead of a screenshot plus a fuzzy memory of the
 * click sequence.
 *
 * Gated by a single static flag (ENABLED) -- flip to false to disable every
 * call site below cheaply, without ripping the instrumentation back out.
 * logEvent() (and therefore logClick()/logShown(), which both funnel through
 * it) does no file I/O at all whenever the flag is off. Not final: a test
 * needs to flip it to prove the off state is genuinely a no-op (see
 * TestInteractionLog) -- production call sites never touch it, only read it.
 *
 * Deliberately NOT SaveStore/GameStateStore's atomic temp-file-then-move
 * pattern: that machinery exists to protect a single canonical snapshot from
 * a torn write, which doesn't apply here -- this is a strictly-append log
 * where every previously-written line is already durable. A plain
 * Files.write(..., CREATE, APPEND), opened and closed on every call rather
 * than held open for the process's lifetime, is enough -- and means there's
 * no long-lived writer that would need flushing/closing from the shutdown
 * hook (Game.main() just calls logEvent() directly from it).
 *
 * The log file itself (default: {@code interaction.log} in the process's
 * working directory -- the repo root, for every run command CLAUDE.md
 * documents) is gitignored; see .gitignore.
 */
public final class InteractionLog {
    /**
     * Off by default -- so constructing UI classes (HamburgerMenu, PauseView,
     * SettingsView, AchievementsView, RulesView, etc.) directly in a JUnit
     * test never does real file I/O against the repo-root log unless a test
     * explicitly opts in (see TestInteractionLog). Game.main() -- the one
     * real process entry point -- flips this to true right before its own
     * "booting up" log call, so real play sessions are still logged by
     * default. Public so it's a one-line edit; non-final so tests can flip it
     * (see class doc).
     */
    public static boolean ENABLED = false;

    private static Path logFile = Paths.get("interaction.log");

    private InteractionLog() {
    }

    /** Test seam: redirect writes to a temp file instead of the real repo-root log -- see TestInteractionLog. */
    static void setLogFileForTest(Path path) {
        logFile = path;
    }

    /** Test seam: restore the real default log file location. */
    static void resetLogFileForTest() {
        logFile = Paths.get("interaction.log");
    }

    /**
     * Logs one click: its raw canvas x/y plus whichever control/hotspot it
     * resolved to (e.g. "BetStepper.INCREMENT", "HamburgerMenu.RULES",
     * "AchievementToast (dismiss)"), or "no control matched" for a genuine
     * miss-click -- raw x/y alone isn't enough to reconstruct what a click
     * actually did.
     */
    public static void logClick(int x, int y, String resolvedControl) {
        logEvent("CLICK (" + x + ", " + y + ") -> " + resolvedControl);
    }

    /** Logs a view/message/prompt becoming visible (a menu opening, a toast appearing, a legality message triggering). */
    public static void logShown(String viewName) {
        logEvent("SHOWN " + viewName);
    }

    /** Shared sink logClick()/logShown() both funnel through -- see class doc for the no-op-when-disabled contract. */
    public static void logEvent(String description) {
        if (!ENABLED) {
            return;
        }
        String line = "[" + Instant.now() + "] " + description + System.lineSeparator();
        try {
            Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
