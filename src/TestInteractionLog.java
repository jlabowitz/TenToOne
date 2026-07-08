import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for InteractionLog: the on/off-flaggable click/event timeline meant
 * to make future bug reports precisely reproducible from a log file (see
 * class doc). Every test redirects writes to a JUnit TemporaryFolder file via
 * the package-private setLogFileForTest() seam (mirrors SaveStore/
 * GameStateStore's own path-injection testability convention) -- never the
 * real repo-root interaction.log -- and restores both the log file and the
 * ENABLED flag in a tearDown, since both are static state shared across the
 * whole test JVM.
 *
 * Doesn't attempt to exhaustively cover every wired-in call site (there are
 * many, all mechanical one-line logClick/logShown calls) -- just the core
 * on/off mechanism plus two representative call sites: HamburgerMenu's
 * showBlocking (a click-loop with its own "shown" + resolved-selection
 * logging) and IllegalPlayFeedback.trigger() (a "message shown" call site
 * with no click loop of its own).
 */
public class TestInteractionLog {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private boolean originalEnabled;

    @After
    public void tearDown() {
        InteractionLog.ENABLED = originalEnabled;
        InteractionLog.resetLogFileForTest();
    }

    private Path redirectToTempFile(String name) throws IOException {
        originalEnabled = InteractionLog.ENABLED;
        Path tempFile = tempFolder.newFile(name).toPath();
        InteractionLog.setLogFileForTest(tempFile);
        return tempFile;
    }

    @Test
    public void loggingWritesALineWhenEnabled() throws IOException {
        Path tempFile = redirectToTempFile("enabled.log");
        InteractionLog.ENABLED = true;

        InteractionLog.logEvent("test event");

        List<String> lines = Files.readAllLines(tempFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("test event"));
    }

    @Test
    public void loggingIsANoOpWhenDisabled() throws IOException {
        Path tempFile = redirectToTempFile("disabled.log");
        InteractionLog.ENABLED = false;

        InteractionLog.logEvent("should never appear");

        assertFalse("no file I/O at all -- the redirected temp file must stay untouched/empty",
                Files.size(tempFile) > 0);
    }

    @Test
    public void logClickFormatsCoordinatesAndResolvedControl() throws IOException {
        Path tempFile = redirectToTempFile("click.log");
        InteractionLog.ENABLED = true;

        InteractionLog.logClick(123, 456, "BetStepper.INCREMENT");

        String line = Files.readAllLines(tempFile).get(0);
        assertTrue(line.contains("CLICK (123, 456) -> BetStepper.INCREMENT"));
    }

    @Test
    public void logShownFormatsViewName() throws IOException {
        Path tempFile = redirectToTempFile("shown.log");
        InteractionLog.ENABLED = true;

        InteractionLog.logShown("PauseView");

        String line = Files.readAllLines(tempFile).get(0);
        assertTrue(line.contains("SHOWN PauseView"));
    }

    /** Representative call site 1: IllegalPlayFeedback.trigger() logs its own "shown" line. */
    @Test
    public void illegalPlayFeedbackTriggerLogsShown() throws IOException {
        Path tempFile = redirectToTempFile("illegal.log");
        InteractionLog.ENABLED = true;

        IllegalPlayFeedback feedback = new IllegalPlayFeedback();
        feedback.trigger("You must follow suit -- play a Hearts card.");

        List<String> lines = Files.readAllLines(tempFile);
        assertTrue(lines.stream().anyMatch(l ->
                l.contains("SHOWN IllegalPlayFeedback: You must follow suit -- play a Hearts card.")));
    }

    /** Representative call site 2: HamburgerMenu.showBlocking() logs its own opening plus the resolved selection. */
    @Test
    public void hamburgerMenuShowBlockingLogsShownAndSelection() throws IOException, InterruptedException {
        Path tempFile = redirectToTempFile("hamburger.log");
        InteractionLog.ENABLED = true;

        Handler handler = new Handler();
        MouseInput mouseInput = new MouseInput();
        AchievementToast toast = new AchievementToast();

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            deliverClick(mouseInput, 30, 20); // row 0: Pause (ROW_TOP=11, ROW_HEIGHT=30 -> row 0 spans y=[11,41))
        });
        clicker.start();

        HamburgerMenu.Selection selection = HamburgerMenu.showBlocking(handler, mouseInput, toast);
        clicker.join();

        assertEquals(HamburgerMenu.Selection.PAUSE, selection);
        List<String> lines = Files.readAllLines(tempFile);
        assertTrue("must log the menu opening", lines.stream().anyMatch(l -> l.contains("SHOWN HamburgerMenu")));
        assertTrue("must log the resolved selection",
                lines.stream().anyMatch(l -> l.contains("CLICK (30, 20) -> HamburgerMenu.PAUSE")));
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same pattern as TestHamburgerMenu. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        java.awt.Component dummy = new java.awt.Canvas();
        mouseInput.mousePressed(new java.awt.event.MouseEvent(dummy, java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }
}
