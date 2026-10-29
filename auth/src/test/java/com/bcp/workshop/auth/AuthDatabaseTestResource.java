package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.sqlite.JDBC;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class AuthDatabaseTestResource implements QuarkusTestResourceLifecycleManager {
    private static final String DATABASE_URL = "jdbc:sqlite:target/auth-test.db";
    private static final String QUOTED_USERNAME = "o'connor";
    private static final String QUOTED_PASSWORD = "QuotedCredential123!";
    private Driver sqliteDriver;

    @Override
    public Map<String, String> start() {
        try {
            sqliteDriver = new JDBC();
            DriverManager.registerDriver(sqliteDriver);
            try (Connection connection = DriverManager.getConnection(DATABASE_URL);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
                statement.executeUpdate("DELETE FROM legacy_users");
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT OR REPLACE INTO legacy_users(username,password,display_name,role) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, QUOTED_USERNAME);
                    insert.setString(2, QUOTED_PASSWORD);
                    insert.setString(3, "Quoted User");
                    insert.setString(4, "STUDENT");
                    insert.executeUpdate();
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to prepare isolated auth test database", error);
        }
        return Map.of();
    }

    @Override
    public void stop() {
        if (sqliteDriver == null) {
            return;
        }
        try {
            DriverManager.deregisterDriver(sqliteDriver);
            sqliteDriver = null;
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to release isolated auth test database driver", error);
        }
    }
}
