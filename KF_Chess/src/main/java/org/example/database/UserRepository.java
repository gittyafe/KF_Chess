package org.example.database;

import java.sql.*;

/**
 * All direct SQL access to the {@code users} table. DatabaseManager only
 * owns the connection and schema; this class is the single place that knows
 * the table's columns and how to read/write them.
 */
public class UserRepository {

    /**
     * Authenticates an existing user, or auto-registers a new one at 1200
     * ELO. Returns the user's rating, or -1 if the password didn't match
     * (or a database error occurred).
     */
    public synchronized int authenticateOrRegister(String username, String password) {
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
            System.err.println("❌ Auth error: " + e.getMessage());
            return -1;
        }
    }

    private int registerNewUser(String username, String password) {
        String insertSQL = "INSERT INTO users(username, password, rating) VALUES(?, ?, 1200)";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            System.out.println("🆕 Registered new user: " + username + " (1200 ELO)");
            return 1200;
        } catch (SQLException e) {
            System.err.println("❌ Registration error: " + e.getMessage());
            return -1;
        }
    }

    public int getRating(String username) {
        String sql = "SELECT rating FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("rating");
        } catch (SQLException e) {
            System.err.println("❌ Error fetching rating: " + e.getMessage());
        }
        return 1200;
    }

    public void updateRating(String username, int newRating) {
        String sql = "UPDATE users SET rating = ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newRating);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error updating rating: " + e.getMessage());
        }
    }

    public synchronized void addRating(String username, int ratingToAdd) {
        String sql = "UPDATE users SET rating = rating + ? WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ratingToAdd);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error updating rating: " + e.getMessage());
        }
    }
}
