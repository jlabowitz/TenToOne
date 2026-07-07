import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * design/persistent-game-state.md §5: reads/writes GameStateSnapshot as JSON
 * (via Json.write/parse), sibling to SaveStore and mirroring its exact
 * pattern -- {@code <user.home>/.tentoone}, atomic temp-file-then-move
 * writes, never throws out to the caller. Parameterized by slotId the same
 * way SaveStore.resolveSaveFile(String profileName) is: today only
 * DEFAULT_SLOT is ever actually used, but a future multi-saved-game feature
 * (or item 4's multi-profile support) needs no storage-format migration,
 * just a different resolveGameStateFile(slotId) call.
 *
 * Deliberately decoupled from GameStateCodec.fromSnapshot: this class only
 * persists the plain-data GameStateSnapshot tree, never touches live
 * Player/Round/Trick objects or PlayerArchetypeRegistry -- a corrupt/
 * malformed file here falls back to Optional.empty() (this class's own
 * concern), while an unknown archetypeId inside an otherwise well-formed
 * snapshot is GameStateCodec.fromSnapshot's concern (a separate failure
 * mode, once the caller actually tries to reconstruct live state from a
 * successfully-loaded snapshot).
 */
public class GameStateStore {
    /** Today's one implicit slot -- see this class's own doc. */
    public static final String DEFAULT_SLOT = "current";

    private final Path gameStateFile;

    /** Parameterized by slotId so a future multi-saved-game feature just calls this with a different slot -- no format/storage-layer change needed later. */
    public static Path resolveGameStateFile(String slotId) throws IOException {
        return SaveStore.resolveAppDataDir().resolve("gamestate-" + slotId + ".json");
    }

    /** Today's implicit single-slot default -- resolves to exactly {@code <user.home>/.tentoone/gamestate-current.json}. */
    public static Path resolveGameStateFile() throws IOException {
        return resolveGameStateFile(DEFAULT_SLOT);
    }

    /** Resolves the real default game-state-file location, creating the app-data directory if needed. */
    public GameStateStore() {
        this(resolveDefaultGameStateFileOrThrow());
    }

    /** Test seam (and general entry point): operate directly against a given game-state-file path. */
    public GameStateStore(Path gameStateFile) {
        this.gameStateFile = gameStateFile;
    }

    private static Path resolveDefaultGameStateFileOrThrow() {
        try {
            return resolveGameStateFile();
        } catch (IOException e) {
            // Only reachable if the app-data directory can't be created at all
            // (e.g. disk full/permissions) -- mirrors SaveStore's identical
            // constructor-time failure mode.
            throw new UncheckedIOException("Could not resolve/create the save-data directory", e);
        }
    }

    /**
     * Loads a GameStateSnapshot from disk, never throwing out to the caller:
     * a missing file, an unreadable file, or malformed JSON/structure all
     * fall back to Optional.empty() -- mirrors SaveStore.load()'s convention.
     */
    @SuppressWarnings("unchecked")
    public Optional<GameStateSnapshot> load() {
        if (!Files.exists(gameStateFile)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(gameStateFile, StandardCharsets.UTF_8);
            Object parsed = Json.parse(text);
            if (!(parsed instanceof Map)) {
                return Optional.empty();
            }
            return Optional.of(mapToSnapshot((Map<String, Object>) parsed));
        } catch (IOException | RuntimeException e) {
            // IOException (unreadable file) / RuntimeException (malformed
            // JSON, wrong field types, bad enum names, etc.) -- never
            // propagate a corrupt file out to the caller, same as
            // SaveStore.parse()'s whole-file-falls-back-to-defaults contract.
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Writes SNAPSHOT atomically: serialize to a temp file in the game-state
     * file's own directory, then Files.move with ATOMIC_MOVE, falling back to
     * a plain (non-atomic) REPLACE_EXISTING move if the filesystem doesn't
     * support atomic moves across these paths -- copies SaveStore.save()'s
     * exact approach. Never throws out to the caller -- a failed checkpoint
     * save must not interrupt gameplay.
     */
    public void save(GameStateSnapshot snapshot) {
        String text = Json.write(snapshotToMap(snapshot));
        try {
            Path dir = gameStateFile.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tempFile = Files.createTempFile(dir, "gamestate", ".tmp");
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            try {
                Files.move(tempFile, gameStateFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, gameStateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean exists() {
        return Files.exists(gameStateFile);
    }

    /** Removes the saved game (if any) -- used at game-completion and explicit-restart. Idempotent: a missing file is a no-op, not an error. */
    public void clear() {
        try {
            Files.deleteIfExists(gameStateFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- GameStateSnapshot <-> generic Map/List tree (see Json's own doc) ---

    private static Map<String, Object> snapshotToMap(GameStateSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("saveFormatVersion", snapshot.saveFormatVersion);
        map.put("savedAt", snapshot.savedAt == null ? null : snapshot.savedAt.toString());
        map.put("roundIndex", snapshot.roundIndex);
        map.put("roundStartingPlayer", snapshot.roundStartingPlayer);
        map.put("roundsHitBonusThisGame", snapshot.roundsHitBonusThisGame);
        map.put("wasSoleLastAtHalfway", snapshot.wasSoleLastAtHalfway);
        List<Object> players = new ArrayList<>();
        for (PlayerSnapshot ps : snapshot.players) {
            players.add(playerSnapshotToMap(ps));
        }
        map.put("players", players);
        map.put("round", snapshot.round == null ? null : roundSnapshotToMap(snapshot.round));
        return map;
    }

    private static Map<String, Object> playerSnapshotToMap(PlayerSnapshot ps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", ps.name);
        map.put("archetypeId", ps.archetypeId);
        map.put("score", ps.score);
        map.put("bet", ps.bet);
        map.put("hasBet", ps.hasBet);
        map.put("trickScore", ps.trickScore);
        List<Object> hand = new ArrayList<>();
        for (CardSnapshot cs : ps.hand) {
            hand.add(cardSnapshotToMap(cs));
        }
        map.put("hand", hand);
        return map;
    }

    private static Map<String, Object> cardSnapshotToMap(CardSnapshot cs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("suit", cs.suit.name());
        map.put("value", cs.value.name());
        return map;
    }

    private static Map<String, Object> roundSnapshotToMap(RoundSnapshot rs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("trumpCard", cardSnapshotToMap(rs.trumpCard));
        map.put("trumpBroken", rs.trumpBroken);
        map.put("currentPlayer", rs.currentPlayer);
        map.put("currentTrick", rs.currentTrick == null ? null : trickSnapshotToMap(rs.currentTrick));
        return map;
    }

    private static Map<String, Object> trickSnapshotToMap(TrickSnapshot ts) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("trickStartPlayer", ts.trickStartPlayer);
        map.put("currentPlayer", ts.currentPlayer);
        map.put("leadingSuit", ts.leadingSuit == null ? null : ts.leadingSuit.name());
        List<Object> cardsPlayedBySeat = new ArrayList<>();
        for (SeatCardPlay play : ts.cardsPlayedBySeat) {
            Map<String, Object> playMap = new LinkedHashMap<>();
            playMap.put("seatIndex", play.seatIndex);
            playMap.put("card", cardSnapshotToMap(play.card));
            cardsPlayedBySeat.add(playMap);
        }
        map.put("cardsPlayedBySeat", cardsPlayedBySeat);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static GameStateSnapshot mapToSnapshot(Map<String, Object> map) {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        snapshot.saveFormatVersion = (String) map.getOrDefault("saveFormatVersion", GameStateSnapshot.CURRENT_SAVE_FORMAT_VERSION);
        Object savedAt = map.get("savedAt");
        snapshot.savedAt = savedAt == null ? null : Instant.parse((String) savedAt);
        snapshot.roundIndex = intValue(map.get("roundIndex"));
        snapshot.roundStartingPlayer = intValue(map.get("roundStartingPlayer"));
        snapshot.roundsHitBonusThisGame = intValue(map.get("roundsHitBonusThisGame"));
        snapshot.wasSoleLastAtHalfway = (Boolean) map.get("wasSoleLastAtHalfway");
        for (Object playerObj : (List<Object>) map.get("players")) {
            snapshot.players.add(mapToPlayerSnapshot((Map<String, Object>) playerObj));
        }
        Object roundObj = map.get("round");
        snapshot.round = roundObj == null ? null : mapToRoundSnapshot((Map<String, Object>) roundObj);
        return snapshot;
    }

    private static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    @SuppressWarnings("unchecked")
    private static PlayerSnapshot mapToPlayerSnapshot(Map<String, Object> map) {
        PlayerSnapshot ps = new PlayerSnapshot();
        ps.name = (String) map.get("name");
        ps.archetypeId = (String) map.get("archetypeId");
        ps.score = intValue(map.get("score"));
        ps.bet = intValue(map.get("bet"));
        ps.hasBet = (Boolean) map.get("hasBet");
        ps.trickScore = intValue(map.get("trickScore"));
        for (Object cardObj : (List<Object>) map.get("hand")) {
            ps.hand.add(mapToCardSnapshot((Map<String, Object>) cardObj));
        }
        return ps;
    }

    private static CardSnapshot mapToCardSnapshot(Map<String, Object> map) {
        Suit suit = Suit.valueOf((String) map.get("suit"));
        CardValue value = CardValue.valueOf((String) map.get("value"));
        return new CardSnapshot(suit, value);
    }

    @SuppressWarnings("unchecked")
    private static RoundSnapshot mapToRoundSnapshot(Map<String, Object> map) {
        RoundSnapshot rs = new RoundSnapshot();
        rs.trumpCard = mapToCardSnapshot((Map<String, Object>) map.get("trumpCard"));
        rs.trumpBroken = (Boolean) map.get("trumpBroken");
        rs.currentPlayer = intValue(map.get("currentPlayer"));
        Object trickObj = map.get("currentTrick");
        rs.currentTrick = trickObj == null ? null : mapToTrickSnapshot((Map<String, Object>) trickObj);
        return rs;
    }

    @SuppressWarnings("unchecked")
    private static TrickSnapshot mapToTrickSnapshot(Map<String, Object> map) {
        TrickSnapshot ts = new TrickSnapshot();
        ts.trickStartPlayer = intValue(map.get("trickStartPlayer"));
        ts.currentPlayer = intValue(map.get("currentPlayer"));
        Object leadingSuitObj = map.get("leadingSuit");
        ts.leadingSuit = leadingSuitObj == null ? null : Suit.valueOf((String) leadingSuitObj);
        for (Object playObj : (List<Object>) map.get("cardsPlayedBySeat")) {
            Map<String, Object> playMap = (Map<String, Object>) playObj;
            int seatIndex = intValue(playMap.get("seatIndex"));
            CardSnapshot card = mapToCardSnapshot((Map<String, Object>) playMap.get("card"));
            ts.cardsPlayedBySeat.add(new SeatCardPlay(seatIndex, card));
        }
        return ts;
    }
}
