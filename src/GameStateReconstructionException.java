/**
 * design/persistent-game-state.md §2a/§3: thrown by GameStateCodec.fromSnapshot
 * when a snapshot can't be safely reconstructed -- an unknown/retired
 * archetypeId, or any other structurally invalid snapshot. Callers must treat
 * this as a whole-load failure and fall back to "no saved game," mirroring
 * SaveStore.parse()'s whole-file-falls-back-to-defaults convention, rather
 * than attempting a partial reconstruction.
 */
public class GameStateReconstructionException extends Exception {
    public GameStateReconstructionException(String message) {
        super(message);
    }
}
