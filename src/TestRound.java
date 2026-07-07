import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Round's trick-leader and leading-suit wiring (all-AI players so
 * bet()/playRound() run to completion synchronously with no GUI input to
 * block on).
 */
public class TestRound {
    @Test
    public void roundConstructorMakesStartingPlayerTheTrickLeader() {
        List<Player> players = new ArrayList<>();
        players.add(new AI_Zombie("A"));
        players.add(new AI_Zombie("B"));
        players.add(new AI_Zombie("C"));

        new Round(3, players, 1, Game.WIDTH, Game.HEIGHT, new Handler());

        assertFalse(players.get(0).isTrickLeader());
        assertTrue(players.get(1).isTrickLeader());
        assertFalse(players.get(2).isTrickLeader());
    }

    /**
     * At the winner.wonTrick() call site, the leader flag must move from
     * whoever led the trick to whoever actually won it. With a single-card
     * round (one trick), the winner is unambiguous via getTrickScore().
     */
    @Test
    public void trickLeaderMovesToTrickWinnerAfterPlayRound() {
        List<Player> players = new ArrayList<>();
        players.add(new AI_Zombie("A"));
        players.add(new AI_Zombie("B"));
        Round round = new Round(1, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        round.bet(0, new GameSettings());
        round.playRound();

        Player winner = players.get(0).getTrickScore() == 1 ? players.get(0) : players.get(1);
        Player loser = winner == players.get(0) ? players.get(1) : players.get(0);

        assertTrue(winner.isTrickLeader());
        assertFalse(loser.isTrickLeader());
    }

    /**
     * Regression guard for the explicit reset call right after
     * handler.removeAll(cardsPlayed): once a trick resolves, the led-suit
     * HUD must go back to "no suit led" immediately, not just when the next
     * Trick object happens to be constructed.
     */
    @Test
    public void leadingSuitResetsToNullAfterTrickResolves() {
        List<Player> players = new ArrayList<>();
        players.add(new AI_Zombie("A"));
        players.add(new AI_Zombie("B"));
        Round round = new Round(1, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        round.bet(0, new GameSettings());
        round.playRound();

        for (Player player : players) {
            assertNull(player.getLeadingSuit());
        }
    }

    /**
     * ROADMAP item 10 §7 (design/persistent-game-state.md's own flag that
     * playRound() wasn't yet safe to resume): a Round reconstructed via the
     * reconstruction constructor, with a Trick set mid-way through via
     * setCurrentTrick(), must have playRound() finish the round correctly --
     * no re-deal (the reconstruction constructor never touches a Deck at
     * all, so a re-deal would NPE), no re-asking of the seat that already
     * played this trick (AI_Zombie's own hand only holds what it has left,
     * so re-asking it in the already-resolved iteration -- rather than
     * resuming the existing Trick -- would exhaust that seat's hand a trick
     * early and throw IndexOutOfBoundsException on the following iteration's
     * legalCards().get(0)), and no trumpBroken reset (constructed here as
     * already-true, a non-default value, specifically so a reset back to
     * false would be caught).
     */
    @Test
    public void playRoundResumesMidTrickWithoutRedealingOrResettingTrumpBroken() {
        List<Player> players = new ArrayList<>();
        AI_Zombie a = new AI_Zombie("A");
        AI_Zombie b = new AI_Zombie("B");
        players.add(a);
        players.add(b);

        // Round of 2 cards: A already played its trick-1 card (a TWO of
        // clubs, not restored onto its Hand -- already-played cards are
        // never part of a reconstructed hand) and has one card left for
        // trick 2; B hasn't played yet this trick and still holds both.
        Hand handA = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        handA.addCard(new Card(Suit.CLUBS, CardValue.THREE));
        a.setHand(handA);

        Hand handB = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        handB.addCard(new Card(Suit.CLUBS, CardValue.FOUR));
        handB.addCard(new Card(Suit.CLUBS, CardValue.FIVE));
        b.setHand(handB);

        Card trumpCard = new Card(Suit.HEARTS, CardValue.KING);
        Round round = new Round(2, players, 0, Game.WIDTH, Game.HEIGHT, new Handler(),
                trumpCard, Suit.HEARTS, true); // trumpBroken already true -- must not be reset

        Trick trick = new Trick(players, 0, 1, Suit.HEARTS, true, Suit.CLUBS,
                List.of(new SeatCardPlay(0, new CardSnapshot(Suit.CLUBS, CardValue.TWO))),
                Game.WIDTH, Game.HEIGHT, new Handler());
        round.setCurrentTrick(trick);

        round.playRound();

        assertTrue("trumpBroken must stay true, not get reset to false", round.getTrumpBroken());
        assertEquals("A's hand must be fully played out, not stuck with a leftover card", 0, a.getHand().getNumCards());
        assertEquals("B's hand must be fully played out, not stuck with a leftover card", 0, b.getHand().getNumCards());
        assertEquals("exactly 2 tricks worth of wins must be recorded (not 1, not 3)",
                2, a.getTrickScore() + b.getTrickScore());
    }
}
