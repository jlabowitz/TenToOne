/**
 * design/persistent-game-state.md §2: one card played into the trick
 * currently in progress, and which seat (index into the player list) played
 * it -- needed to reconstruct whose turn is next, ordered as played within
 * TrickSnapshot.cardsPlayedBySeat.
 */
public class SeatCardPlay {
    public int seatIndex;
    public CardSnapshot card;

    public SeatCardPlay() {
    }

    public SeatCardPlay(int seatIndex, CardSnapshot card) {
        this.seatIndex = seatIndex;
        this.card = card;
    }
}
