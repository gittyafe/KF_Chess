package org.example.database;

import lombok.extern.slf4j.Slf4j;

/**
 * Applies the outcome of a finished game to both players' ratings. Sits
 * between GameRoom (which knows *who* won) and UserRepository/EloCalculator
 * (which know how to persist a rating and how to compute one) -- neither of
 * those two should need to know about the other.
 */
@Slf4j
public class RatingService {

    private final UserRepository userRepository;

    public RatingService() {
        this(new UserRepository());
    }

    public RatingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** @param whiteScore 1.0 if white won, 0.0 if black won, 0.5 for a draw */
    public synchronized void applyGameResult(String whiteUser, String blackUser, double whiteScore) {
        try {
            int ratingW = userRepository.getRating(whiteUser);
            int ratingB = userRepository.getRating(blackUser);

            EloCalculator.EloResult result = EloCalculator.calculateNewRatings(ratingW, ratingB, whiteScore);

            userRepository.updateRating(whiteUser, result.newRatingA());
            userRepository.updateRating(blackUser, result.newRatingB());

            log.info("[SERVER OUT] ELO Updated: {} ({}) -> {}) | {} ({} -> {})", whiteUser, ratingW, result.newRatingA(), blackUser, ratingB, result.newRatingB());
        } catch (Exception e) {
            log.error("[SERVER ERROR] Failed to update DB ratings: {}", e.getMessage());
        }
    }
}
