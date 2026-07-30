package org.example.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A repository for managing user data in the database.
 */
@Slf4j
@Repository
public class UserRepository implements DisposableBean {

    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(20, r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });

    private static final int INITIAL_ELO = 1200;
    /**
     * Authenticates an existing user asynchronously, or auto-registers a new one at 1200 ELO.
     */
    public CompletableFuture<Integer> authenticateOrRegisterAsync(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            String selectSQL = "SELECT password, rating FROM users WHERE username = ?";
            try (Connection conn = DatabaseManager.connect();
                 PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String storedPasswordHash = rs.getString("password");
                        return PasswordHasher.verify(password, storedPasswordHash) ? rs.getInt("rating") : -1;
                    }
                }
                return registerNewUser(username, password);

            } catch (SQLException e) {
                log.error("[PostgreSQL ERROR] Auth error for user {}: {}", username, e.getMessage());
                return -1;
            }
        }, dbExecutor);
    }

    private int registerNewUser(String username, String password) {
        String insertSQL = "INSERT INTO users(username, password, rating) VALUES(?, ?, ?) " +
                "ON CONFLICT (username) DO NOTHING";
        String selectSQL = "SELECT password, rating FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.connect()) {
            String passwordHash = PasswordHasher.hash(password);

            try (PreparedStatement insert = conn.prepareStatement(insertSQL)) {
                insert.setString(1, username);
                insert.setString(2, passwordHash);
                insert.setInt(3, INITIAL_ELO);
                int inserted = insert.executeUpdate();
                if (inserted == 1) {
                    log.info("[PostgreSQL] Registered new user: {} ({} ELO)", username, INITIAL_ELO);
                    return INITIAL_ELO;
                }
            }

            // We lost the insert race -- someone else registered this username
            // a moment earlier. Authenticate against their row instead of
            // failing a perfectly valid new-user request.
            try (PreparedStatement select = conn.prepareStatement(selectSQL)) {
                select.setString(1, username);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        String storedPasswordHash = rs.getString("password");
                        return PasswordHasher.verify(password, storedPasswordHash) ? rs.getInt("rating") : -1;
                    }
                }
            }
            return -1;

        } catch (SQLException e) {
            log.error("[PostgreSQL ERROR] Failed to register new user {}: {}", username, e.getMessage());
            return -1;
        }
    }

    public CompletableFuture<Integer> getRatingAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT rating FROM users WHERE username = ?";
            try (Connection conn = DatabaseManager.connect();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("rating");
                }
            } catch (SQLException e) {
                log.error("[PostgreSQL ERROR] Error fetching rating for user {}: {}", username, e.getMessage());
            }
            return 1200;
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateRatingAsync(String username, int newRating) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE users SET rating = ? WHERE username = ?";
            try (Connection conn = DatabaseManager.connect();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, newRating);
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                log.error("[PostgreSQL ERROR] Error updating rating for user {}: {}", username, e.getMessage());
            }
        }, dbExecutor);
    }

    /** Spring calls this on context shutdown so the dedicated pool's threads don't leak. */
    @Override
    public void destroy() {
        dbExecutor.shutdown();
    }
}
