import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * ROADMAP item 27/persistent-game-state design doc §2a: PlayerArchetypeRegistry
 * is the id -> factory lookup GameStateCodec.fromSnapshot uses to reconstruct
 * non-human players from a saved archetypeId.
 */
public class TestPlayerArchetypeRegistry {
    @Test
    public void unknownIdReturnsNull() {
        assertNull(PlayerArchetypeRegistry.lookup("not_a_real_archetype"));
    }

    @Test
    public void humanIsNotRegistered() {
        assertNull("Human must be reconstructed directly, not via this registry", PlayerArchetypeRegistry.lookup("human"));
    }

    @Test
    public void aiEasyBuildsAnAiEasyWithTheGivenName() {
        Function<String, Player> factory = PlayerArchetypeRegistry.lookup("ai_easy");
        assertTrue(factory != null);
        Player player = factory.apply("Bot");
        assertTrue(player instanceof AI_Easy);
        assertEquals("Bot", player.getName());
        assertEquals("ai_easy", player.archetypeId());
    }

    @Test
    public void aiZombieBuildsAnAiZombieWithTheGivenName() {
        Function<String, Player> factory = PlayerArchetypeRegistry.lookup("ai_zombie");
        assertTrue(factory != null);
        Player player = factory.apply("Zed");
        assertTrue(player instanceof AI_Zombie);
        assertEquals("Zed", player.getName());
        assertEquals("ai_zombie", player.archetypeId());
    }

    @Test
    public void mediumBalancedBuildsAnAiMediumWithThatPersonality() {
        Function<String, Player> factory = PlayerArchetypeRegistry.lookup("medium_balanced");
        assertTrue(factory != null);
        Player player = factory.apply("Balanced Bot");
        assertTrue(player instanceof AI_Medium);
        assertEquals("medium_balanced", player.archetypeId());
    }

    @Test
    public void mediumBoldBuildsAnAiMediumWithThatPersonality() {
        Function<String, Player> factory = PlayerArchetypeRegistry.lookup("medium_bold");
        Player player = factory.apply("Bold Bot");
        assertEquals("medium_bold", player.archetypeId());
    }

    @Test
    public void mediumCautiousBuildsAnAiMediumWithThatPersonality() {
        Function<String, Player> factory = PlayerArchetypeRegistry.lookup("medium_cautious");
        Player player = factory.apply("Cautious Bot");
        assertEquals("medium_cautious", player.archetypeId());
    }
}
