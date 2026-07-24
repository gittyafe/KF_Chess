package org.example.network;

public class EloCalculator {

    private static final int K_FACTOR = 32;

    public static class EloResult {
        public double newRatingA;
        public double newRatingB;

        public EloResult(double newRatingA, double newRatingB) {
            this.newRatingA = newRatingA;
            this.newRatingB = newRatingB;
        }
    }

    /**
     * @param ratingA דירוג נוכחי של שחקן א
     * @param ratingB דירוג נוכחי של שחקן ב
     * @param scoreA 1.0 עבור ניצחון א, 0.5 עבור תיקו, 0.0 עבור הפסד א
     */
    public static EloResult calculateNewRatings(double ratingA, double ratingB, double scoreA) {
        // 1. חישוב תוחלות הציפייה
        double expectedA = 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
        double expectedB = 1.0 - expectedA;

        // 2. תוצאה עבור שחקן ב
        double scoreB = 1.0 - scoreA;

        // 3. עדכון הדירוגים
        double newRatingA = ratingA + K_FACTOR * (scoreA - expectedA);
        double newRatingB = ratingB + K_FACTOR * (scoreB - expectedB);

        return new EloResult(newRatingA, newRatingB);
    }
}