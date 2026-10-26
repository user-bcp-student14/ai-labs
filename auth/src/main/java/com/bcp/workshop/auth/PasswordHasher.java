package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ENCODING_ID = "pbkdf2-sha256";
    private static final int ITERATION_COUNT = 600_000;
    private static final int MAXIMUM_ITERATION_COUNT = 1_000_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int HASH_LENGTH_BYTES = HASH_LENGTH_BITS / Byte.SIZE;
    private static final int ENCODED_PARTS = 4;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derivedKey = deriveKey(password, salt, ITERATION_COUNT);
        return String.join("$",
                ENCODING_ID,
                Integer.toString(ITERATION_COUNT),
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derivedKey));
    }

    public boolean matches(String password, String encodedPassword) {
        EncodedPassword parsed = parse(encodedPassword);
        if (parsed == null) {
            return false;
        }
        byte[] candidate = deriveKey(password, parsed.salt(), parsed.iterations());
        return MessageDigest.isEqual(parsed.derivedKey(), candidate);
    }

    public boolean isEncoded(String encodedPassword) {
        return parse(encodedPassword) != null;
    }

    private EncodedPassword parse(String encodedPassword) {
        if (encodedPassword == null) {
            return null;
        }
        String[] parts = encodedPassword.split("\\$", -1);
        if (parts.length != ENCODED_PARTS || !ENCODING_ID.equals(parts[0])) {
            return null;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] derivedKey = Base64.getDecoder().decode(parts[3]);
            if (iterations < ITERATION_COUNT || iterations > MAXIMUM_ITERATION_COUNT
                    || salt.length != SALT_LENGTH_BYTES || derivedKey.length != HASH_LENGTH_BYTES) {
                return null;
            }
            return new EncodedPassword(iterations, salt, derivedKey);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private byte[] deriveKey(String password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to hash password", error);
        } finally {
            keySpec.clearPassword();
        }
    }

    private record EncodedPassword(int iterations, byte[] salt, byte[] derivedKey) {}
}
