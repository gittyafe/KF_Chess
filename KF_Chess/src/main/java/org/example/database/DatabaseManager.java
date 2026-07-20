package org.example.database;

import java.sql.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chess_game.db";

    static {
        initDatabase();
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "username TEXT PRIMARY KEY, "
                + "password TEXT NOT NULL, "
                + "rating INTEGER DEFAULT 1200"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("📦 SQLite DB initialized successfully.");
        } catch (SQLException e) {
            System.err.println("❌ Database init error: " + e.getMessage());
        }
    }

    /**
     * מאמת משתמש. אם אינו קיים – רושם אותו אוטומטית עם ELO 1200.
     * אם קיים – בודק תאימות סיסמה.
     * @return ה-ELO הנוכחי של המשתמש, או 1- אם הסיסמה שגויה.
     */
    public static synchronized int authenticateOrRegister(String username, String password) {
        String selectSQL = "SELECT password, rating FROM users WHERE username = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // המשתמש קיים - בדיקת סיסמה
                String storedPassword = rs.getString("password");
                if (storedPassword.equals(password)) {
                    return rs.getInt("rating"); // אימות הצליח
                } else {
                    return -1; // סיסמה שגויה
                }
            } else {
                // משתמש חדש - הרשמה עם 1200 ELO התחלתי
                return registerNewUser(username, password);
            }
        } catch (SQLException e) {
            System.err.println("❌ Auth error: " + e.getMessage());
            return -1;
        }
    }

    private static int registerNewUser(String username, String password) {
        String insertSQL = "INSERT INTO users(username, password, rating) VALUES(?, ?, 1200)";
        try (Connection conn = connect();
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

    /**
     * שליפת דירוג ELO של משתמש
     */
    public static int getRating(String username) {
        String sql = "SELECT rating FROM users WHERE username = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("rating");
        } catch (SQLException e) {
            System.err.println("❌ Error fetching rating: " + e.getMessage());
        }
        return 1200;
    }

    /**
     * עדכון ELO לאחר ניצחון/הפסד/תיקו
     */
    public static synchronized void updateRatings(String whiteUser, String blackUser, double whiteScore) {
        int ratingW = getRating(whiteUser);
        int ratingB = getRating(blackUser);

        // נוסחת ELO
        double expectedW = 1.0 / (1.0 + Math.pow(10, (ratingB - ratingW) / 400.0));
        double expectedB = 1.0 - expectedW;

        int kFactor = 32;
        int newRatingW = (int) Math.round(ratingW + kFactor * (whiteScore - expectedW));
        int newRatingB = (int) Math.round(ratingB + kFactor * ((1.0 - whiteScore) - expectedB));

        updateUserRating(whiteUser, newRatingW);
        updateUserRating(blackUser, newRatingB);

        System.out.printf("🏆 ELO Updated: %s (%d -> %d) | %s (%d -> %d)%n",
                whiteUser, ratingW, newRatingW, blackUser, ratingB, newRatingB);
    }

    private static void updateUserRating(String username, int newRating) {
        String sql = "UPDATE users SET rating = ? WHERE username = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newRating);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error updating rating: " + e.getMessage());
        }
    }
}