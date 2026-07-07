import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for GameStateStore (design/persistent-game-state.md §5) -- mirrors
 * TestSaveStore's style exactly (temp-file seams, no real {@code ~/.tentoone}
 * touched). Covers the JSON round-trip, corrupt-file fallback, and
 * exists()/clear() behavior.
 */
public class TestGameStateStore {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GameStateSnapshot sampleSnapshot() {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        snapshot.roundIndex = 4;
        snapshot.roundStartingPlayer = 2;
        snapshot.roundsHitBonusThisGame = 3;
        snapshot.wasSoleLastAtHalfway = true;

        PlayerSnapshot human = new PlayerSnapshot();
        human.name = "Alice";
        human.archetypeId = "human";
        human.score = 55;
        human.bet = 2;
        human.hasBet = true;
        human.trickScore = 1;
        human.hand.add(new CardSnapshot(Suit.HEARTS, CardValue.ACE));
        human.hand.add(new CardSnapshot(Suit.SPADES, CardValue.TWO));
        snapshot.players.add(human);

        PlayerSnapshot ai = new PlayerSnapshot();
        ai.name = "Bot";
        ai.archetypeId = "medium_bold";
        ai.score = 30;
        ai.bet = 0;
        ai.hasBet = false;
        ai.trickScore = 0;
        snapshot.players.add(ai);

        RoundSnapshot round = new RoundSnapshot();
        round.trumpCard = new CardSnapshot(Suit.CLUBS, CardValue.KING);
        round.trumpBroken = true;
        round.currentPlayer = 1;

        TrickSnapshot trick = new TrickSnapshot();
        trick.trickStartPlayer = 1;
        trick.currentPlayer = 0;
        trick.leadingSuit = Suit.CLUBS;
        trick.cardsPlayedBySeat.add(new SeatCardPlay(1, new CardSnapshot(Suit.CLUBS, CardValue.FIVE)));
        round.currentTrick = trick;

        snapshot.round = round;
        return snapshot;
    }

    @Test
    public void loadReturnsEmptyWhenFileIsMissing() throws IOException {
        Path file = tempFolder.newFolder("missing").toPath().resolve("gamestate.json");
        GameStateStore store = new GameStateStore(file);

        assertTrue(store.load().isEmpty());
        assertFalse(store.exists());
    }

    @Test
    public void writeThenReadRoundTripsEveryField() throws IOException {
        Path file = tempFolder.newFolder("roundtrip").toPath().resolve("gamestate.json");
        GameStateStore store = new GameStateStore(file);
        GameStateSnapshot original = sampleSnapshot();

        store.save(original);
        assertTrue(store.exists());

        Optional<GameStateSnapshot> loaded = store.load();
        assertTrue(loaded.isPresent());
        GameStateSnapshot reloaded = loaded.get();

        assertEquals(4, reloaded.roundIndex);
        assertEquals(2, reloaded.roundStartingPlayer);
        assertEquals(3, reloaded.roundsHitBonusThisGame);
        assertTrue(reloaded.wasSoleLastAtHalfway);
        assertEquals(GameStateSnapshot.CURRENT_SAVE_FORMAT_VERSION, reloaded.saveFormatVersion);

        assertEquals(2, reloaded.players.size());
        PlayerSnapshot human = reloaded.players.get(0);
        assertEquals("Alice", human.name);
        assertEquals("human", human.archetypeId);
        assertEquals(55, human.score);
        assertEquals(2, human.bet);
        assertTrue(human.hasBet);
        assertEquals(1, human.trickScore);
        assertEquals(2, human.hand.size());
        assertEquals(Suit.HEARTS, human.hand.get(0).suit);
        assertEquals(CardValue.ACE, human.hand.get(0).value);

        PlayerSnapshot ai = reloaded.players.get(1);
        assertEquals("medium_bold", ai.archetypeId);
        assertFalse(ai.hasBet);

        assertTrue(reloaded.round != null);
        assertEquals(Suit.CLUBS, reloaded.round.trumpCard.suit);
        assertEquals(CardValue.KING, reloaded.round.trumpCard.value);
        assertTrue(reloaded.round.trumpBroken);
        assertEquals(1, reloaded.round.currentPlayer);

        assertTrue(reloaded.round.currentTrick != null);
        assertEquals(1, reloaded.round.currentTrick.trickStartPlayer);
        assertEquals(0, reloaded.round.currentTrick.currentPlayer);
        assertEquals(Suit.CLUBS, reloaded.round.currentTrick.leadingSuit);
        assertEquals(1, reloaded.round.currentTrick.cardsPlayedBySeat.size());
        assertEquals(1, reloaded.round.currentTrick.cardsPlayedBySeat.get(0).seatIndex);
        assertEquals(CardValue.FIVE, reloaded.round.currentTrick.cardsPlayedBySeat.get(0).card.value);
    }

    @Test
    public void roundTripsANullRoundBetweenRounds() throws IOException {
        Path file = tempFolder.newFolder("noround").toPath().resolve("gamestate.json");
        GameStateStore store = new GameStateStore(file);
        GameStateSnapshot snapshot = sampleSnapshot();
        snapshot.round = null;

        store.save(snapshot);
        GameStateSnapshot reloaded = store.load().orElseThrow();

        assertEquals(null, reloaded.round);
    }

    @Test
    public void loadFallsBackToEmptyOnMalformedJson() throws IOException {
        Path file = tempFolder.newFolder("malformed").toPath().resolve("gamestate.json");
        Files.writeString(file, "{not valid json", StandardCharsets.UTF_8);
        GameStateStore store = new GameStateStore(file);

        assertTrue("malformed JSON must fall back to empty, not throw", store.load().isEmpty());
    }

    @Test
    public void loadFallsBackToEmptyWhenTopLevelValueIsNotAnObject() throws IOException {
        Path file = tempFolder.newFolder("notobject").toPath().resolve("gamestate.json");
        Files.writeString(file, "[1, 2, 3]", StandardCharsets.UTF_8);
        GameStateStore store = new GameStateStore(file);

        assertTrue(store.load().isEmpty());
    }

    @Test
    public void loadFallsBackToEmptyOnMissingRequiredField() throws IOException {
        Path file = tempFolder.newFolder("missingfield").toPath().resolve("gamestate.json");
        Files.writeString(file, "{\"roundIndex\": 1}", StandardCharsets.UTF_8);
        GameStateStore store = new GameStateStore(file);

        assertTrue("a structurally incomplete file must fall back to empty, not throw",
                store.load().isEmpty());
    }

    @Test
    public void clearDeletesTheFileAndIsIdempotentWhenAlreadyMissing() throws IOException {
        Path file = tempFolder.newFolder("clear").toPath().resolve("gamestate.json");
        GameStateStore store = new GameStateStore(file);
        store.save(sampleSnapshot());
        assertTrue(store.exists());

        store.clear();
        assertFalse(store.exists());

        // idempotent -- clearing an already-missing file must not throw
        store.clear();
        assertFalse(store.exists());
    }

    @Test
    public void saveDoesNotLeaveATempFileBehind() throws IOException {
        Path dir = tempFolder.newFolder("atomic").toPath();
        Path file = dir.resolve("gamestate.json");
        GameStateStore store = new GameStateStore(file);

        store.save(sampleSnapshot());

        try (var entries = Files.list(dir)) {
            long fileCount = entries.count();
            assertEquals("only the final game-state file should remain, no leftover temp file", 1, fileCount);
        }
    }

    @Test
    public void resolveGameStateFileWithSlotIdUsesThatSlotsFile() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        try {
            Path tempHome = tempFolder.newFolder("home").toPath();
            System.setProperty("user.home", tempHome.toString());

            Path defaultFile = GameStateStore.resolveGameStateFile();
            Path namedSlotFile = GameStateStore.resolveGameStateFile("secondary");

            assertEquals(tempHome.resolve(".tentoone").resolve("gamestate-current.json"), defaultFile);
            assertEquals(tempHome.resolve(".tentoone").resolve("gamestate-secondary.json"), namedSlotFile);
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }
}
