package org.example.database;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;

/**
 * Owns the SQLite connection and schema only. Query/update logic for
 * specific tables lives in repository classes (see UserRepository) so this
 * class doesn't grow a new method every time a new table or query shows up.
 */
@Slf4j
public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chess_game.db";

    static {
        initDatabase();
    }

    public static Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);

        // הגדרת המתנה של 5,000 מילישניות לפני שזה זורק SQLITE_BUSY
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA journal_mode = WAL;");
        }

        return conn;
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
            log.info("[SQLite OUT] DB initialized successfully.");
        } catch (SQLException e) {
            log.error("[SQLite ERROR] Database init error: " + e.getMessage());
        }
    }
}
