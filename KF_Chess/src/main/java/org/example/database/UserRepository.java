package org.example.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What changed and why:
 * <ul>
 *   <li><b>Dedicated executor.</b> Every async method here used to run on
 *       {@code CompletableFuture}'s default executor --
 *       {@code ForkJoinPool.commonPool()} -- which is shared by every
 *       unrelated use of {@code CompletableFuture}/parallel streams in the
 *       whole JVM. A slow or momentarily-stuck JDBC call could starve
 *       completely unrelated async work elsewhere in the app. Now runs on
 *       a small dedicated pool, sized to roughly match
 *       {@code DatabaseManager}'s HikariCP pool (20), so it can never have
 *       more concurrent DB work in flight than the connection pool can
 *       actually serve.</li>
 *   <li><b>Password hashing.</b> Passwords were stored and compared as
 *       plain text (a fresh column value equal to whatever the client
 *       sent, and {@code String.equals} against it). Now hashed with
 *       PBKDF2 via {@link PasswordHasher}. <b>Migration note:</b> any
 *       existing rows in the {@code users} table have plaintext passwords
 *       in the {@code password} column -- {@code PasswordHasher.verify}
 *       will not match them (wrong format), so those accounts will need a
 *       password reset (or a one-time migration script that re-hashes
 *       them) after this change ships.</li>
 *   <li><b>Race-safe registration.</b> {@code registerNewUser} used to do
 *       a plain {@code INSERT}; if two requests for the same brand-new
 *       username arrived concurrently (double-click login, a login racing
 *       a reconnect, etc.), the second one hit a duplicate-key
 *       {@code SQLException}, which was logged and turned into a flat
 *       {@code -1} ("invalid password or database error") -- even though
 *       the username itself was perfectly valid and the user simply lost
 *       an insert race for their own new account. Now uses
 *       {@code INSERT ... ON CONFLICT (username) DO NOTHING}, and if the
 *       insert didn't win, falls back to authenticating against whichever
 *       row did.</li>
 *   <li>Removed {@code authenticateOrRegister(String alice, String
 *       wrong)} -- a dead, no-op method with hardcoded parameter names
 *       that looked like leftover test/debug code that shouldn't have
 *       been committed.</li>
 * </ul>
 */
@Slf4j
@Repository
public class UserRepository implements DisposableBean {

    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(20, r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });

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
        String insertSQL = "INSERT INTO users(username, password, rating) VALUES(?, ?, 1200) " +
                "ON CONFLICT (username) DO NOTHING";
        String selectSQL = "SELECT password, rating FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.connect()) {
            String passwordHash = PasswordHasher.hash(password);

            try (PreparedStatement insert = conn.prepareStatement(insertSQL)) {
                insert.setString(1, username);
                insert.setString(2, passwordHash);
                int inserted = insert.executeUpdate();
                if (inserted == 1) {
                    log.info("[PostgreSQL] Registered new user: {} (1200 ELO)", username);
                    return 1200;
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
