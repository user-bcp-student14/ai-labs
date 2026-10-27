package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
public class JwtTokenService {
    static final int TOKEN_TTL_SECONDS = 3600;
    private static final int MIN_SECRET_LENGTH_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";
    private static final String JWT_TYPE = "JWT";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid token";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "auth.token.secret")
    String secret;

    @PostConstruct
    void validateConfiguration() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must contain at least 32 UTF-8 bytes");
        }
    }

    public String create(String username, String role) {
        return create(username, role, Instant.now());
    }

    String create(String username, String role, Instant issuedAt) {
        if (username == null || username.isBlank() || role == null || role.isBlank()) {
            throw new IllegalArgumentException("Token subject and role are required");
        }

        long issuedAtEpochSecond = issuedAt.getEpochSecond();
        long expiresAtEpochSecond = issuedAt.plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String encodedHeader = encodeJson(Map.of("alg", JWT_ALGORITHM, "typ", JWT_TYPE));
        String encodedClaims = encodeJson(Map.of(
                "sub", username,
                "role", role,
                "iat", issuedAtEpochSecond,
                "exp", expiresAtEpochSecond));
        String signingInput = encodedHeader + "." + encodedClaims;
        String signature = URL_ENCODER.encodeToString(sign(signingInput));
        return signingInput + "." + signature;
    }

    public TokenClaims verify(String token) {
        return verify(token, Instant.now());
    }

    TokenClaims verify(String token, Instant now) {
        if (token == null) {
            throw invalidToken();
        }

        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            throw invalidToken();
        }

        byte[] suppliedSignature;
        try {
            suppliedSignature = URL_DECODER.decode(segments[2]);
        } catch (IllegalArgumentException error) {
            throw invalidToken();
        }

        String signingInput = segments[0] + "." + segments[1];
        if (!MessageDigest.isEqual(sign(signingInput), suppliedSignature)) {
            throw invalidToken();
        }

        JsonNode header = decodeJson(segments[0]);
        if (header == null || !header.isObject()
                || !JWT_ALGORITHM.equals(header.path("alg").asText())
                || !JWT_TYPE.equals(header.path("typ").asText())) {
            throw invalidToken();
        }

        JsonNode claims = decodeJson(segments[1]);
        String subject = requiredText(claims, "sub");
        String role = requiredText(claims, "role");
        long issuedAtEpochSecond = requiredLong(claims, "iat");
        long expiresAtEpochSecond = requiredLong(claims, "exp");
        long nowEpochSecond = now.getEpochSecond();

        if (expiresAtEpochSecond <= issuedAtEpochSecond
                || expiresAtEpochSecond <= nowEpochSecond
                || issuedAtEpochSecond > nowEpochSecond) {
            throw invalidToken();
        }

        return new TokenClaims(subject, role, issuedAtEpochSecond, expiresAtEpochSecond);
    }

    private String encodeJson(Object value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize token claims", error);
        }
    }

    private JsonNode decodeJson(String segment) {
        try {
            return objectMapper.readTree(URL_DECODER.decode(segment));
        } catch (IllegalArgumentException | IOException error) {
            throw invalidToken();
        }
    }

    private String requiredText(JsonNode claims, String name) {
        JsonNode value = claims == null ? null : claims.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalidToken();
        }
        return value.asText();
    }

    private long requiredLong(JsonNode claims, String name) {
        JsonNode value = claims == null ? null : claims.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalidToken();
        }
        return value.asLong();
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to sign authentication token", error);
        }
    }

    private InvalidTokenException invalidToken() {
        return new InvalidTokenException();
    }

    public record TokenClaims(
            String subject,
            String role,
            long issuedAtEpochSecond,
            long expiresAtEpochSecond) {}

    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super(INVALID_TOKEN_MESSAGE);
        }
    }
}
