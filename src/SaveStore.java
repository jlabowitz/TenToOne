import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

/**
 * ROADMAP item 2: reads/writes SaveData to a Properties file. Read once at
 * startup; write is triggered by the caller (Game) after game-end, round-end,
 * and name-submission -- never deferred to process exit, since this codebase
 * has no clean-shutdown hook.
 *
 * Two independent seams, both deliberate:
 * - resolveAppDataDir()/resolveSaveFile() are static, reusable utilities (a
 *   future feature -- ROADMAP item 10 -- will share the same app-data
 *   directory) that touch the real filesystem (creating
 *   {@code <user.home>/.tentoone} if missing) and the real "user.home"
 *   system property. Tests exercise these by temporarily overriding
 *   "user.home" to a temp directory rather than ever pointing at the real
 *   home directory -- see TestSaveStore.
 * - The instance itself is constructed from a concrete save-file Path (the
 *   no-arg constructor resolves the real default path; a test constructs
 *   directly from a JUnit temp file instead), so load()/save() never need to
 *   touch the real filesystem location in a test.
 */
public class SaveStore {
    /** Produces exactly {@code save.properties} via resolveSaveFile(String) -- today's one implicit profile. */
    public static final String DEFAULT_PROFILE = "save";

    private final Path saveFile;

    /** Resolves (and creates, if missing) the shared app-data directory {@code <user.home>/.tentoone}. */
    public static Path resolveAppDataDir() throws IOException {
        Path dir = Paths.get(System.getProperty("user.home"), ".tentoone");
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Parameterized by profile name so a future multi-profile feature (not in
     * scope now) just calls this with a different profile name -- no format/
     * storage-layer change needed later.
     */
    public static Path resolveSaveFile(String profileName) throws IOException {
        return resolveAppDataDir().resolve(profileName + ".properties");
    }

    /** Today's implicit single-profile default -- resolves to exactly {@code <user.home>/.tentoone/save.properties}. */
    public static Path resolveSaveFile() throws IOException {
        return resolveSaveFile(DEFAULT_PROFILE);
    }

    /** Resolves the real default save-file location, creating the app-data directory if needed. */
    public SaveStore() {
        this(resolveDefaultSaveFileOrThrow());
    }

    /** Test seam (and general entry point): operate directly against a given save-file path. */
    public SaveStore(Path saveFile) {
        this.saveFile = saveFile;
    }

    private static Path resolveDefaultSaveFileOrThrow() {
        try {
            return resolveSaveFile();
        } catch (IOException e) {
            // Only reachable if the app-data directory can't be created at all
            // (e.g. disk full/permissions) -- a genuinely exceptional setup
            // failure, distinct from "missing/corrupt save file" (which
            // load() below handles gracefully rather than throwing).
            throw new UncheckedIOException("Could not resolve/create the save-data directory", e);
        }
    }

    /**
     * Loads SaveData from disk, never throwing out to the caller: a missing
     * file, an unreadable file, or a file with malformed field values all
     * fall back to SaveData.defaults().
     */
    public SaveData load() {
        if (!Files.exists(saveFile)) {
            return SaveData.defaults();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(saveFile)) {
            props.load(in);
        } catch (IOException e) {
            e.printStackTrace();
            return SaveData.defaults();
        }
        return parse(props);
    }

    /**
     * Whole-file fallback to defaults on any parse failure (a malformed
     * numeric field, a malformed achievement timestamp, etc.) rather than
     * trying to salvage individual fields -- simpler and avoids ending up
     * with an internally-inconsistent SaveData.
     */
    private SaveData parse(Properties props) {
        try {
            SaveData data = new SaveData();
            data.saveFormatVersion = props.getProperty("saveFormatVersion", SaveData.CURRENT_SAVE_FORMAT_VERSION);
            data.gamesPlayed = Integer.parseInt(props.getProperty("stats.gamesPlayed", "0"));
            data.gamesWon = Integer.parseInt(props.getProperty("stats.gamesWon", "0"));
            data.highScore = Integer.parseInt(props.getProperty("stats.highScore", "0"));
            data.currentWinStreak = Integer.parseInt(props.getProperty("stats.currentWinStreak", "0"));
            data.bestWinStreakEver = Integer.parseInt(props.getProperty("stats.bestWinStreakEver", "0"));
            data.totalPoints = Integer.parseInt(props.getProperty("stats.totalPoints", "0"));
            data.totalRoundsBet = Integer.parseInt(props.getProperty("stats.totalRoundsBet", "0"));
            data.totalRoundsBetHit = Integer.parseInt(props.getProperty("stats.totalRoundsBetHit", "0"));
            data.lastUsedName = props.getProperty("profile.lastUsedName", "");
            for (Achievement achievement : Achievement.values()) {
                String value = props.getProperty("achievement." + achievement.name() + ".unlockedAt");
                if (value != null) {
                    data.unlockedAchievements.put(achievement.name(), Instant.parse(value));
                }
            }
            return data;
        } catch (RuntimeException e) {
            // NumberFormatException / DateTimeParseException / etc. -- never
            // propagate a corrupt file out to the caller.
            e.printStackTrace();
            return SaveData.defaults();
        }
    }

    /**
     * Writes DATA atomically: serialize to a temp file in the save file's own
     * directory, then Files.move with ATOMIC_MOVE, falling back to a plain
     * (non-atomic) REPLACE_EXISTING move if the filesystem doesn't support
     * atomic moves across these paths. Never throws out to the caller -- a
     * failed save shouldn't crash gameplay (matches Card.loadImage's
     * print-and-continue fallback style).
     */
    public void save(SaveData data) {
        Properties props = toProperties(data);
        try {
            Path dir = saveFile.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tempFile = Files.createTempFile(dir, "save", ".tmp");
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                props.store(out, "Ten to One save data -- do not edit by hand");
            }
            try {
                Files.move(tempFile, saveFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, saveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Properties toProperties(SaveData data) {
        Properties props = new Properties();
        props.setProperty("saveFormatVersion", SaveData.CURRENT_SAVE_FORMAT_VERSION);
        props.setProperty("stats.gamesPlayed", String.valueOf(data.gamesPlayed));
        props.setProperty("stats.gamesWon", String.valueOf(data.gamesWon));
        props.setProperty("stats.highScore", String.valueOf(data.highScore));
        props.setProperty("stats.currentWinStreak", String.valueOf(data.currentWinStreak));
        props.setProperty("stats.bestWinStreakEver", String.valueOf(data.bestWinStreakEver));
        props.setProperty("stats.totalPoints", String.valueOf(data.totalPoints));
        props.setProperty("stats.totalRoundsBet", String.valueOf(data.totalRoundsBet));
        props.setProperty("stats.totalRoundsBetHit", String.valueOf(data.totalRoundsBetHit));
        props.setProperty("profile.lastUsedName", data.lastUsedName == null ? "" : data.lastUsedName);
        for (Map.Entry<String, Instant> entry : data.unlockedAchievements.entrySet()) {
            props.setProperty("achievement." + entry.getKey() + ".unlockedAt", entry.getValue().toString());
        }
        return props;
    }
}
