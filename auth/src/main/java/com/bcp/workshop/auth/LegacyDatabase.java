package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class LegacyDatabase {
    private static final Logger LOGGER = Logger.getLogger(LegacyDatabase.class.getName());
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String CREATE_USERS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS legacy_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE,
                password TEXT NOT NULL,
                display_name TEXT NOT NULL,
                role TEXT NOT NULL
            )
            """;
    private static final String INSERT_USER_SQL = """
            INSERT OR IGNORE INTO legacy_users(username,password,display_name,role)
            VALUES (?, ?, ?, ?)
            """;
    private static final String SELECT_CREDENTIALS_SQL = """
            SELECT id, username, password, display_name, role
            FROM legacy_users
            WHERE username = ?
            """;
    private static final String UPDATE_PASSWORD_SQL = "UPDATE legacy_users SET password = ? WHERE id = ?";
    private static final String SELECT_PASSWORDS_SQL = "SELECT id, password FROM legacy_users";
    private static final String STUDENT_USERNAME = "student";
    private static final String STUDENT_PASSWORD = "Workshop123!";
    private static final String STUDENT_DISPLAY_NAME = "Workshop Student";
    private static final String STUDENT_ROLE = "STUDENT";
    private static final String TRAINER_USERNAME = "trainer";
    private static final String TRAINER_PASSWORD = "Trainer123!";
    private static final String TRAINER_DISPLAY_NAME = "Workshop Trainer";
    private static final String TRAINER_ROLE = "TRAINER";

    @Inject
    PasswordHasher passwordHasher;

    @ConfigProperty(name = "auth.seed-lab-users", defaultValue = "false")
    boolean seedLabUsers;

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_USERS_TABLE_SQL);
                migratePlaintextPasswords(connection);
                if (seedLabUsers) {
                    seedUser(connection, STUDENT_USERNAME, STUDENT_PASSWORD, STUDENT_DISPLAY_NAME, STUDENT_ROLE);
                    seedUser(connection, TRAINER_USERNAME, TRAINER_PASSWORD, TRAINER_DISPLAY_NAME, TRAINER_ROLE);
                }
            }
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("Unable to initialize authentication database", error);
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(SELECT_CREDENTIALS_SQL)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !passwordHasher.matches(password, result.getString("password"))) {
                    return null;
                }
                return new UserRecord(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("display_name"),
                        result.getString("role"));
            }
        } catch (SQLException error) {
            LOGGER.log(Level.SEVERE, "Unable to query authentication database", error);
            return null;
        }
    }

    private void migratePlaintextPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> legacyPasswords = loadLegacyPasswords(connection);
        try (PreparedStatement update = connection.prepareStatement(UPDATE_PASSWORD_SQL)) {
            for (LegacyPassword legacyPassword : legacyPasswords) {
                if (passwordHasher.isEncoded(legacyPassword.value())) {
                    continue;
                }
                update.setString(1, passwordHasher.hash(legacyPassword.value()));
                update.setLong(2, legacyPassword.userId());
                update.executeUpdate();
            }
        }
    }

    private List<LegacyPassword> loadLegacyPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> legacyPasswords = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(SELECT_PASSWORDS_SQL)) {
            while (result.next()) {
                legacyPasswords.add(new LegacyPassword(result.getLong("id"), result.getString("password")));
            }
        }
        return legacyPasswords;
    }

    private void seedUser(Connection connection, String username, String password, String displayName, String role)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_USER_SQL)) {
            insert.setString(1, username);
            insert.setString(2, passwordHasher.hash(password));
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }

    private record LegacyPassword(long userId, String value) {}
}
