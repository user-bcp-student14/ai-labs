package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.eclipse.microprofile.config.ConfigProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(AuthDatabaseTestResource.class)
@TestProfile(DevelopmentSeedConfigurationTest.DevelopmentProfile.class)
class DevelopmentSeedConfigurationTest {
    private static final String TEST_DATABASE_URL = "jdbc:sqlite:target/auth-test.db";

    @Inject
    LegacyDatabase database;

    @Test
    void seedsLabAccountsInDevelopmentProfile() throws Exception {
        assertTrue(ConfigProvider.getConfig().getValue("auth.seed-lab-users", Boolean.class));
        database.initialize();
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM legacy_users WHERE username IN (?, ?)")) {
            statement.setString(1, "student");
            statement.setString(2, "trainer");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(2, result.getInt(1));
            }
        }
    }

    public static class DevelopmentProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "dev";
        }
    }
}
