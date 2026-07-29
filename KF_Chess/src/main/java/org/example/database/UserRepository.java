package org.example.database;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class UserRepository {

    /**
     * Authenticates an existing user asynchronously, or auto-registers a new one at 1200 ELO.
     */
    public CompletableFuture<Integer> authenticateOrRegisterAsync(String username, String password) {
        return CompletableFuture.supplyAsync(() -> {
            String selectSQL = "SELECT password, rating FROM users WHERE username = ?";
            try (Connection conn = DatabaseManager.connect();
                 PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String storedPassword = rs.getString("password");
                    return storedPassword.equals(password) ? rs.getInt("rating") : -1;
                }
                return registerNewUser(username, password);

            } catch (SQLException e) {
                log.error("[PostgreSQL ERROR] Auth error for user {}: {}", username, e.getMessage());
                return -1;
            }
        });
    }

    private int registerNewUser(String username, String password) {
        String insertSQL = "INSERT INTO users(username, password, rating) VALUES(?, ?, 1200)";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            log.info("[PostgreSQL] Registered new user: {} (1200 ELO)", username);
            return 1200;
        } catch (SQLException e) {
            log.error("[PostgreSQL ERROR] Failed to register new user: {}", e.getMessage());
            return -1;
        }
    }

    public CompletableFuture<Integer> getRatingAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT rating FROM users WHERE username = ?";
            try (Connection conn = DatabaseManager.connect();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return rs.getInt("rating");
            } catch (SQLException e) {
                log.error("[PostgreSQL ERROR] Error fetching rating for user {}: {}", username, e.getMessage());
            }
            return 1200;
        });
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
        });
    }
}