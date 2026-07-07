/**
 * ROADMAP item 1 (design/ai-and-polish.md §7): a thin flavor layer over the
 * AI tier classes (design doc §1) -- each named personality carries a small
 * config bundle (display name + numeric tuning knobs), and the tier class
 * (AI_Medium today; AI_Hard/AI_Expert later) takes that config as a
 * constructor parameter rather than being hard-coded.
 *
 * Fields marked "inert for now" below are wired into this record exactly as
 * the design doc specifies, even though nothing reads them yet -- AI_Medium
 * only consumes riskTolerance/opponentBetTrust. This avoids a breaking
 * schema change once AI_Hard/AI_Expert (ROADMAP item 21, deferred) land and
 * start reading recallCapacity/recallAccuracy/the off-suit-tracking fields.
 */
public record AIPersonality(
        String id,                        // ROADMAP item 27/persistent-game-state design doc §2a --
                                           // stable, permanent, decoupled from `name` (cosmetic, not final)
        String name,
        int tier,
        double riskTolerance,             // §4.5
        double opponentBetTrust,          // §4.2 -- baseTrust
        int recallCapacity,               // §6.4, inert until AI_Hard/AI_Expert (item 21)
        double recallAccuracy,            // §6.4, inert until item 21
        boolean offSuitTrackingEnabled,   // §6.5, inert until item 21
        double offSuitAccuracyMultiplier, // §6.5, inert until item 21
        int offSuitRelevanceThreshold,    // §6.5, inert until item 21
        double voidProgressWeight,        // v2 doc §0.7/§2.7 -- reacting-branch suit-void tiebreak, case 2
        double cardValueWeight,           // v2 doc §0.7/§2.7 -- reacting-branch suit-void tiebreak, case 2
        double valueNormalizationScale,   // v2 doc §0.7/§2.7 -- reacting-branch suit-void tiebreak, case 2
        double trumpWeightK                // v2 doc §0.7/§4.3 -- naturalBet() trump-weighting dial,
                                            // PLACEHOLDER (needs a real tuning/playtesting pass, see AI_Medium's class doc)
) {
    /**
     * Medium tier (design doc §1's tier == 2). Descriptive-not-final
     * placeholder names -- per §7, real personality naming needs the user's
     * explicit sign-off, not yet given. These exist to prove the config
     * layer actually changes AI_Medium's behavior (see TestAIMedium), not as
     * a finished personality roster.
     *
     * voidProgressWeight/cardValueWeight/valueNormalizationScale (v2 doc
     * §2.7) and trumpWeightK (v2 doc §4.3) are shared starting values across
     * all three personalities below -- none of them varies these dials by
     * personality yet (unlike riskTolerance/opponentBetTrust). trumpWeightK
     * in particular is an explicit placeholder (0.5): the user has said this
     * needs a real tuning/playtesting pass later, not a settled value.
     */
    public static final AIPersonality MEDIUM_BALANCED =
            new AIPersonality("medium_balanced", "Balanced", 2, 0.5, 0.8, 0, 0.0, false, 0.0, 10, 0.5, 1.0, 12.0, 0.5);
    public static final AIPersonality MEDIUM_BOLD =
            new AIPersonality("medium_bold", "Bold", 2, 0.9, 0.8, 0, 0.0, false, 0.0, 10, 0.5, 1.0, 12.0, 0.5);
    public static final AIPersonality MEDIUM_CAUTIOUS =
            new AIPersonality("medium_cautious", "Cautious", 2, 0.1, 0.8, 0, 0.0, false, 0.0, 10, 0.5, 1.0, 12.0, 0.5);
}
