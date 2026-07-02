import java.awt.Point;
import java.util.List;
import java.util.Scanner;

public class Human extends Player{
    private final MouseInput mouseInput;
    private final Scanner playerInput = new Scanner(System.in);

    public Human(String name, MouseInput mouseInput) {
        super(name);
        this.mouseInput = mouseInput;
        id = ID.HUMAN;
    }


    @Override
    public void bet(Suit trump) {
        int maxBet = getHand().getNumCards();
        System.out.println(getHand());
        System.out.println(getName() + ", what do you want to bet? (0-" + maxBet + ")");

        while (true) {
            if (!playerInput.hasNextInt()) {
                playerInput.next();
                System.out.println("Please enter a whole number between 0 and " + maxBet + ".");
                continue;
            }

            int bet = playerInput.nextInt();
            if (!isValidBet(bet, maxBet)) {
                System.out.println("Bet must be between 0 and " + maxBet + ".");
                continue;
            }

            setBet(bet);
            return;
        }
    }

    static boolean isValidBet(int bet, int maxBet) {
        return bet >= 0 && bet <= maxBet;
    }

    @Override
    public Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        System.out.println(getHand());
        System.out.println(getName() + ", click the card you want to play.");

        List<Card> legal = legalCards(cardsPlayed, leading, trump, trumpBroken);
        mouseInput.clearClicks();
        while (true) {
            Point click = mouseInput.awaitClick();
            Card card = getHand().cardAt(click.x, click.y);
            if (card == null) {
                continue;
            }
            if (!legal.contains(card)) {
                System.out.println("The " + card + " is not a legal play.");
                continue;
            }
            getHand().playCard(card);
            System.out.println(getName() + " played the " + card);
            return card;
        }
    }

    @Override
    public void nextTrick() {
        System.out.println("Click anywhere to move on to the next trick.");
        mouseInput.clearClicks();
        mouseInput.awaitClick();
    }
}
