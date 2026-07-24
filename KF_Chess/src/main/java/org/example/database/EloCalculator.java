package org.example.database;

/**
 * Pure ELO rating math. No I/O, no persistence -- just the formula, so it
 * can be reasoned about (and unit tested) independently of the database.
 * (The commented-out reference to this class already sitting in the old
 * DatabaseManager.updateRatings suggests it used to exist as its own class
 * before the math got inlined -- this restores that split.)
 */
public final class EloCalculator {

    private static final int K_FACTOR = 32;

    public record EloResult(int newRatingA, int newRatingB) {}

    private EloCalculator() {}

    /**
     * @param ratingA current rating of player A
     * @param ratingB current rating of player B
     * @param scoreA  A's result: 1.0 = win, 0.0 = loss, 0.5 = draw
     */
    public static EloResult calculateNewRatings(int ratingA, int ratingB, double scoreA) {
        double expectedA = 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
        double expectedB = 1.0 - expectedA;
        double scoreB = 1.0 - scoreA;

        int newRatingA = (int) Math.round(ratingA + K_FACTOR * (scoreA - expectedA));
        int newRatingB = (int) Math.round(ratingB + K_FACTOR * (scoreB - expectedB));

        return new EloResult(newRatingA, newRatingB);
    }
}
