import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for SaveStore (ROADMAP item 2). All read/write tests use a JUnit
 * TemporaryFolder, never the real {@code ~/.tentoone} -- see the
 * resolveAppDataDir/resolveSaveFile tests below for the one exception, which
 * temporarily overrides the "user.home" system property to a temp directory
 * for the duration of the test (restored in a finally) rather than ever
 * pointing at the real home directory.
 */
public class TestSaveStore {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String originalUserHome;

    @After
    public void restoreUserHome() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
            originalUserHome = null;
        }
    }

    // --- resolveAppDataDir / resolveSaveFile: path-resolution contract ---

    @Test
    public void resolveAppDataDirCreatesDotTentooneUnderUserHome() throws IOException {
        Path tempHome = tempFolder.newFolder("home").toPath();
        overrideUserHome(tempHome);

        Path dir = SaveStore.resolveAppDataDir();

        assertEquals(tempHome.resolve(".tentoone"), dir);
        assertTrue("resolveAppDataDir must create the directory if missing", Files.isDirectory(dir));
    }

    @Test
    public void resolveSaveFileNoArgDefaultsToExactlySaveDotProperties() throws IOException {
        Path tempHome = tempFolder.newFolder("home2").toPath();
        overrideUserHome(tempHome);

        Path file = SaveStore.resolveSaveFile();

        assertEquals(tempHome.resolve(".tentoone").resolve("save.properties"), file);
    }

    @Test
    public void resolveSaveFileWithProfileNameUsesThatProfilesFile() throws IOException {
        Path tempHome = tempFolder.newFolder("home3").toPath();
        overrideUserHome(tempHome);

        Path file = SaveStore.resolveSaveFile("alice");

        assertEquals(tempHome.resolve(".tentoone").resolve("alice.properties"), file);
    }

    private void overrideUserHome(Path tempHome) {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());
    }

    // --- load(): defaults / round-trip / corrupt-file fallback ---

    @Test
    public void loadReturnsDefaultsWhenSaveFileIsMissing() throws IOException {
        Path saveFile = tempFolder.newFolder("missing").toPath().resolve("save.properties");
        SaveStore store = new SaveStore(saveFile);

        SaveData data = store.load();

        assertEquals(0, data.gamesPlayed);
        assertEquals(0, data.highScore);
        assertFalse(data.isUnlocked(Achievement.FIRST_VICTORY));
    }

    @Test
    public void writeThenReadRoundTripsStatsAndUnlockedAchievements() throws IOException {
        Path saveFile = tempFolder.newFolder("roundtrip").toPath().resolve("save.properties");
        SaveStore store = new SaveStore(saveFile);

        SaveData original = SaveData.defaults();
        original.gamesPlayed = 7;
        original.gamesWon = 4;
        original.highScore = 123;
        original.currentWinStreak = 2;
        original.bestWinStreakEver = 5;
        original.totalPoints = 543;
        original.totalRoundsBet = 52;
        original.totalRoundsBetHit = 19;
        original.lastUsedName = "Alex";
        original.unlock(Achievement.FIRST_VICTORY);
        original.unlock(Achievement.SCORE_OVER_100);

        store.save(original);
        SaveData reloaded = store.load();

        assertEquals(7, reloaded.gamesPlayed);
        assertEquals(4, reloaded.gamesWon);
        assertEquals(123, reloaded.highScore);
        assertEquals(2, reloaded.currentWinStreak);
        assertEquals(5, reloaded.bestWinStreakEver);
        assertEquals(543, reloaded.totalPoints);
        assertEquals(52, reloaded.totalRoundsBet);
        assertEquals(19, reloaded.totalRoundsBetHit);
        assertEquals("Alex", reloaded.lastUsedName);
        assertEquals(SaveData.CURRENT_SAVE_FORMAT_VERSION, reloaded.saveFormatVersion);
        assertTrue(reloaded.isUnlocked(Achievement.FIRST_VICTORY));
        assertTrue(reloaded.isUnlocked(Achievement.SCORE_OVER_100));
        assertFalse(reloaded.isUnlocked(Achievement.TEN_GAMES_PLAYED));
        assertEquals(original.unlockedAt(Achievement.FIRST_VICTORY), reloaded.unlockedAt(Achievement.FIRST_VICTORY));
    }

    @Test
    public void loadReturnsEmptyLastUsedNameWhenSaveFileIsMissing() throws IOException {
        Path saveFile = tempFolder.newFolder("missing2").toPath().resolve("save.properties");
        SaveStore store = new SaveStore(saveFile);

        SaveData data = store.load();

        assertEquals("", data.lastUsedName);
        assertEquals(0, data.totalPoints);
        assertEquals(0, data.totalRoundsBet);
        assertEquals(0, data.totalRoundsBetHit);
    }

    @Test
    public void savedFileIncludesSaveFormatVersionKey() throws IOException {
        Path saveFile = tempFolder.newFolder("version").toPath().resolve("save.properties");
        SaveStore store = new SaveStore(saveFile);

        store.save(SaveData.defaults());

        String content = Files.readString(saveFile, StandardCharsets.UTF_8);
        assertTrue("written file must carry a saveFormatVersion key", content.contains("saveFormatVersion"));
    }

    @Test
    public void loadFallsBackToDefaultsOnMalformedNumericField() throws IOException {
        Path saveFile = tempFolder.newFolder("corrupt").toPath().resolve("save.properties");
        Files.writeString(saveFile, "stats.gamesPlayed=not-a-number\n", StandardCharsets.UTF_8);
        SaveStore store = new SaveStore(saveFile);

        SaveData data = store.load();

        assertEquals("corrupt file must fall back to defaults, not throw", 0, data.gamesPlayed);
    }

    @Test
    public void loadFallsBackToDefaultsOnGarbageBinaryContent() throws IOException {
        Path saveFile = tempFolder.newFolder("garbage").toPath().resolve("save.properties");
        Files.write(saveFile, new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01, 0x02});
        SaveStore store = new SaveStore(saveFile);

        SaveData data = store.load();

        assertEquals(0, data.gamesPlayed);
        assertFalse(data.isUnlocked(Achievement.FIRST_VICTORY));
    }

    // --- save(): atomicity/no-half-written-file sanity checks ---

    @Test
    public void saveDoesNotLeaveATempFileBehind() throws IOException {
Path dir = tempFolder.newFolder("atomic").toPath();
        Path saveFile = dir.resolve("save.properties");
        SaveStore store = new SaveStore(saveFile);

        store.save(SaveData.defaults());

        try (var entries = Files.list(dir)) {
            long fileCount = entries.count();
            assertEquals("only the final save file should remain, no leftover temp file", 1, fileCount);
        }
    }

    @Test
    public void saveProducesAParsableFileImmediately() throws IOException {
        Path saveFile = tempFolder.newFolder("valid").toPath().resolve("save.properties");
        SaveStore store = new SaveStore(saveFile);
        SaveData data = SaveData.defaults();
        data.gamesWon = 3;

        store.save(data);

        assertTrue(Files.exists(saveFile));
        SaveData reloaded = store.load();
        assertEquals(3, reloaded.gamesWon);
    }

    @Test
    public void savingTwiceOverwritesThePreviousContent() throws IOException {
        Path saveFile = tempFolder.newFolder("overwrite").toPath().resolve("save.properties");
        SaveStore store = new SaveStore(saveFile);

        SaveData first = SaveData.defaults();
        first.gamesPlayed = 1;
        store.save(first);

        SaveData second = SaveData.defaults();
        second.gamesPlayed = 2;
        store.save(second);

        assertEquals(2, store.load().gamesPlayed);
    }
}
