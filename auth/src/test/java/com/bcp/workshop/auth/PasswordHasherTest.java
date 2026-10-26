package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private static final String TEST_PASSWORD = "TestPassword123!";

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesPasswordWithUniqueSaltAndVerifiesIt() {
        String firstHash = passwordHasher.hash(TEST_PASSWORD);
        String secondHash = passwordHasher.hash(TEST_PASSWORD);

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.isEncoded(firstHash));
        assertTrue(passwordHasher.matches(TEST_PASSWORD, firstHash));
        assertFalse(passwordHasher.matches("WrongPassword", firstHash));
    }

    @Test
    void rejectsLegacyAndMalformedHashes() {
        assertFalse(passwordHasher.isEncoded(null));
        assertFalse(passwordHasher.isEncoded("plaintext-password"));
        assertFalse(passwordHasher.isEncoded("pbkdf2-sha256$invalid$salt$key"));
        assertFalse(passwordHasher.isEncoded("pbkdf2-sha256$1$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        assertFalse(passwordHasher.isEncoded("pbkdf2-sha256$600000$%%%$%%%"));
        assertFalse(passwordHasher.isEncoded("pbkdf2-sha256$600000$AA==$AA=="));
        assertFalse(passwordHasher.matches(TEST_PASSWORD, "plaintext-password"));
    }
}
