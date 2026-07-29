package org.example.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages PostgreSQL connection pool (HikariCP) and schema initialization.
 */
@Slf4j
public class DatabaseManager {
    // פרטי ההתחברות ל-PostgreSQL שהרמנו ב-docker-compose
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/kfchess";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "password";

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

        // הגדרות Connection Pool מותאמות עומס
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setDriverClassName("org.postgresql.Driver");

        dataSource = new HikariDataSource(config);

        initDatabase();
    }

    public static Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    public static void initDatabase() {
        // תחביר תואם PostgreSQL
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "username VARCHAR(50) PRIMARY KEY, "
                + "password VARCHAR(255) NOT NULL, "
                + "rating INT DEFAULT 1200"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            log.info("[PostgreSQL] DB initialized successfully.");
        } catch (SQLException e) {
            log.error("[PostgreSQL ERROR] Database init error: {}", e.getMessage());
        }
    }
}