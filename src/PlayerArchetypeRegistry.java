import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ROADMAP item 27/persistent-game-state design doc §2a: a small, static id
 * -> factory map used by GameStateCodec.fromSnapshot to build the right
 * non-human Player given just an archetypeId + name. Ids are append-only --
 * adding a new AI is just adding a new id + entry here; retiring one means
 * leaving its id unregistered (see lookup()'s doc for what that means to a
 * caller).
 *
 * Deliberately does NOT register "human" -- Human's constructor needs live
 * UI collaborators (MouseInput, Handler, AchievementToast, SaveData, plus a
 * checkpoint-save callback) that a generic {@code name -> Player} factory
 * can't supply. Whoever reconstructs a Human does so directly, branching on
 * {@code archetypeId.equals("human")}, not via this registry.
 */
public final class PlayerArchetypeRegistry {
    private static final Map<String, Function<String, Player>> FACTORIES = new HashMap<>();

    static {
        FACTORIES.put(AI_Easy.ARCHETYPE_ID, AI_Easy::new);
        FACTORIES.put(AI_Zombie.ARCHETYPE_ID, AI_Zombie::new);
        FACTORIES.put(AIPersonality.MEDIUM_BALANCED.id(),
                name -> new AI_Medium(name, AIPersonality.MEDIUM_BALANCED));
        FACTORIES.put(AIPersonality.MEDIUM_BOLD.id(),
                name -> new AI_Medium(name, AIPersonality.MEDIUM_BOLD));
        FACTORIES.put(AIPersonality.MEDIUM_CAUTIOUS.id(),
                name -> new AI_Medium(name, AIPersonality.MEDIUM_CAUTIOUS));
    }

    private PlayerArchetypeRegistry() {
    }

    /**
     * Looks up archetypeId's factory, or returns null if it isn't registered
     * (an unknown/retired/corrupt id). Callers (GameStateCodec.fromSnapshot)
     * must treat a null result as a whole-load failure, not attempt a partial
     * reconstruction or silent substitution -- per the design doc's explicit
     * "unknown id on load" contract, mirroring SaveStore.parse()'s
     * whole-file-falls-back-to-defaults convention.
     */
    public static Function<String, Player> lookup(String archetypeId) {
        return FACTORIES.get(archetypeId);
    }
}
