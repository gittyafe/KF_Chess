package org.example.database;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RatingService {

    private final UserRepository userRepository;

    public RatingService() {
        this(new UserRepository());
    }

    public RatingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Non-blocking update of player ratings after a game.
     */
    public void applyGameResultAsync(String whiteUser, String blackUser, double whiteScore) {
        CompletableFuture<Integer> whiteRatingFuture = userRepository.getRatingAsync(whiteUser);
        CompletableFuture<Integer> blackRatingFuture = userRepository.getRatingAsync(blackUser);

        // ממתינים לקבלת שני הדירוגים אסינכרונית, ומחשבים ELO
        CompletableFuture.allOf(whiteRatingFuture, blackRatingFuture)
                .thenRunAsync(() -> {
                    try {
                        int ratingW = whiteRatingFuture.join();
                        int ratingB = blackRatingFuture.join();

                        EloCalculator.EloResult result = EloCalculator.calculateNewRatings(ratingW, ratingB, whiteScore);

                        // עדכון ה-DB ברקע
                        userRepository.updateRatingAsync(whiteUser, result.newRatingA());
                        userRepository.updateRatingAsync(blackUser, result.newRatingB());

                        log.info("[SERVER OUT] ELO Updated Async: {} ({}) -> {}) | {} ({} -> {})",
                                whiteUser, ratingW, result.newRatingA(), blackUser, ratingB, result.newRatingB());
                    } catch (Exception e) {
                        log.error("[SERVER ERROR] Failed to update DB ratings async: {}", e.getMessage());
                    }
                });
    }
}