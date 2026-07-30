package org.example.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages PostgreSQL connection pool (HikariCP) and schema initialization.
 *
 * <p>What changed and why:
 * <ul>
 *   <li>URL/user/password were hardcoded, forcing every environment (dev,
 *       staging, prod, every developer's laptop) to share the same
 *       database and credentials, and making it impossible to point at a
 *       different host in Docker/K8s without a code change and rebuild.
 *       Now read from env vars the same way {@code NatsBridge} already
 *       does it, with the old literal values kept only as local-dev
 *       fallbacks.</li>
 *   <li>{@code initDatabase()} used to catch the schema-creation
 *       {@code SQLException}, log it, and otherwise continue as if
 *       nothing happened -- the process would come up "successfully" with
 *       an unknown/missing schema, and the first real query later would
 *       fail with a confusing, disconnected-looking error. It now rethrows
 *       as an unchecked exception, so a bad DB/schema is a clear, loud
 *       startup failure instead of a delayed mystery.</li>
 * </ul>
 */
@Slf4j
public class DatabaseManager {

    private static final String DB_URL =
            System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/kfchess");
    private static final String DB_USER =
            System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASSWORD =
            System.getenv().getOrDefault("DB_PASSWORD", "password");

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

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
            throw new IllegalStateException(
                    "Failed to initialize PostgreSQL schema at " + DB_URL +
                            " -- refusing to start with an unknown schema state.", e);
        }
    }
}
