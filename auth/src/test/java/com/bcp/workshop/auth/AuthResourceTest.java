package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(AuthDatabaseTestResource.class)
class AuthResourceTest {
    private static final String TEST_DATABASE_URL = "jdbc:sqlite:target/auth-test.db";
    private static final String QUOTED_USERNAME = "o'connor";
    private static final String QUOTED_PASSWORD = "QuotedCredential123!";
    private static final String APPROVED_CORS_ORIGIN = "http://localhost:8080";
    private static final String UNAPPROVED_CORS_ORIGIN = "https://unapproved.example";
    private static final String ACCESS_CONTROL_ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    @Inject
    LegacyDatabase database;

    @Inject
    JwtTokenService tokenService;

    @Test
    void reportsHealthAsUpWithoutAuthentication() {
        io.restassured.response.Response response = given()
                .when().get("/q/health")
                .then()
                .extract().response();

        assertEquals(200, response.statusCode());
        assertEquals("UP", response.jsonPath().getString("status"));
    }

    @Test
    void reportsAnEmptyHealthCheckList() {
        io.restassured.response.Response response = given()
                .when().get("/q/health")
                .then()
                .extract().response();

        assertEquals(200, response.statusCode());
        assertTrue(response.jsonPath().getList("checks").isEmpty());
    }

    @Test
    void rejectsUnapprovedCorsOriginOnHealth() {
        io.restassured.response.Response response = given()
                .header("Origin", UNAPPROVED_CORS_ORIGIN)
                .when().get("/q/health")
                .then()
                .extract().response();

        assertEquals(403, response.statusCode());
        assertEquals("", response.asString());
        assertFalse(response.getHeaders().hasHeaderWithName(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void allowsCorsForApprovedOrigin() {
        io.restassured.response.Response response = loginFromOrigin(APPROVED_CORS_ORIGIN);

        assertEquals(401, response.statusCode());
        assertEquals(APPROVED_CORS_ORIGIN, response.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
    }

    @Test
    void doesNotAllowCorsForUnapprovedOrigin() {
        io.restassured.response.Response response = loginFromOrigin(UNAPPROVED_CORS_ORIGIN);

        assertEquals(403, response.statusCode());
        assertEquals("", response.asString());
        assertFalse(response.getHeaders().hasHeaderWithName(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
    }

    @Test
    void loginReturnsVerifiableJwtWithExpectedClaims() {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().path("token");

        assertEquals(3, token.split("\\.", -1).length);
        JwtTokenService.TokenClaims claims = tokenService.verify(token);

        assertEquals("student", claims.subject());
        assertEquals("STUDENT", claims.role());
        assertEquals(JwtTokenService.TOKEN_TTL_SECONDS,
                claims.expiresAtEpochSecond() - claims.issuedAtEpochSecond());
    }

    @Test
    void verifiesSignedJwtClaims() {
        Instant issuedAt = Instant.parse("2026-10-26T12:00:00Z");
        String token = tokenService.create("student", "STUDENT", issuedAt);

        JwtTokenService.TokenClaims claims = tokenService.verify(token, issuedAt.plusSeconds(1));

        assertEquals("student", claims.subject());
        assertEquals("STUDENT", claims.role());
        assertEquals(issuedAt.getEpochSecond(), claims.issuedAtEpochSecond());
        assertEquals(issuedAt.plusSeconds(JwtTokenService.TOKEN_TTL_SECONDS).getEpochSecond(),
                claims.expiresAtEpochSecond());
    }

    @Test
    void rejectsJwtWithModifiedSignature() {
        String[] segments = tokenService.create("student", "STUDENT").split("\\.");
        char firstSignatureCharacter = segments[2].charAt(0);
        segments[2] = (firstSignatureCharacter == 'A' ? "B" : "A") + segments[2].substring(1);

        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify(String.join(".", segments)));
    }

    @Test
    void rejectsExpiredJwt() {
        Instant issuedAt = Instant.parse("2026-10-26T12:00:00Z");
        String token = tokenService.create("student", "STUDENT", issuedAt);

        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify(token, issuedAt.plusSeconds(JwtTokenService.TOKEN_TTL_SECONDS)));
    }

    @Test
    void rejectsMalformedJwt() {
        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify("malformed-token"));
    }

    @Test
    void rejectsNullJwt() {
        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify(null, Instant.now()));
    }

    @Test
    void rejectsInvalidBase64Signature() {
        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify("header.payload.%$", Instant.now()));
    }

    @Test
    void rejectsInvalidJwtAlgorithm() throws Exception {
        String claims = validClaimsJson();
        String token = signTestJwt("{\"alg\":\"HS512\",\"typ\":\"JWT\"}", claims);

        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify(token, Instant.now()));
    }

    @Test
    void rejectsMalformedJwtHeader() throws Exception {
        String token = signTestJwt("not-json", validClaimsJson());

        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify(token, Instant.now()));
    }

    @Test
    void rejectsJwtWithoutSubjectClaim() throws Exception {
        long now = Instant.now().getEpochSecond();
        String claims = "{\"role\":\"STUDENT\",\"iat\":" + (now - 1)
                + ",\"exp\":" + (now + 60) + "}";
        String token = signTestJwt("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", claims);

        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify(token, Instant.now()));
    }

    @Test
    void rejectsJwtWithoutExpirationClaim() throws Exception {
        long now = Instant.now().getEpochSecond();
        String claims = "{\"sub\":\"student\",\"role\":\"STUDENT\",\"iat\":" + (now - 1) + "}";
        String token = signTestJwt("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", claims);

        assertThrows(JwtTokenService.InvalidTokenException.class,
                () -> tokenService.verify(token, Instant.now()));
    }

    @Test
    void rejectsInvalidTokenSecretConfiguration() {
        JwtTokenService invalidService = new JwtTokenService();
        invalidService.secret = "too-short";

        assertThrows(IllegalStateException.class, invalidService::validateConfiguration);
    }

    @Test
    void rejectsTokenCreationWithoutSubject() {
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.create("", "STUDENT", Instant.now()));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"missing-user\",\"password\":\"WrongPassword\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid username or password"));
    }

    @Test
    void rejectsRequestWithoutUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsRequestWithoutPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsBlankUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"  \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"  \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsNullRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsEmptyHttpRequestBody() {
        given()
                .contentType("application/json")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMalformedJsonWithEmptyErrorBody() {
        io.restassured.response.Response response = given()
                .contentType("application/json")
                .body("{")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .extract().response();

        assertEquals("", response.asString());
    }

    @Test
    void rejectsUnsupportedContentTypeWithEmptyErrorBody() {
        io.restassured.response.Response response = given()
                .contentType("text/plain")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(415)
                .extract().response();

        assertEquals("", response.asString());
    }

    @Test
    void rejectsUnsupportedAcceptTypeWithEmptyErrorBody() {
        io.restassured.response.Response response = given()
                .accept("text/plain")
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(406)
                .extract().response();

        assertEquals("", response.asString());
    }

    @Test
    void rejectsUnsupportedMethodWithEmptyErrorBody() {
        io.restassured.response.Response response = given()
                .when().get("/auth/login")
                .then()
                .statusCode(405)
                .extract().response();

        assertEquals("", response.asString());
    }

    @Test
    void authenticatesUsernameContainingQuoteAsLiteralData() {
        given()
                .contentType("application/json")
                .body(Map.of("username", QUOTED_USERNAME, "password", QUOTED_PASSWORD))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is(QUOTED_USERNAME));
    }

    @Test
    void doesNotStoreSeededPasswordAsPlainText() throws Exception {
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, "student");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertNotEquals("Workshop123!", result.getString("password"));
            }
        }
    }

    @Test
    void migratesExistingQuotedPasswordWithoutBreakingLogin() throws Exception {
        given()
                .contentType("application/json")
                .body(Map.of("username", QUOTED_USERNAME, "password", QUOTED_PASSWORD))
                .when().post("/auth/login")
                .then()
                .statusCode(200);

        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, QUOTED_USERNAME);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertNotEquals(QUOTED_PASSWORD, result.getString("password"));
            }
        }
    }

    @Test
    void passwordMigrationIsIdempotent() throws Exception {
        database.initialize();
        String hashBefore = storedPassword(QUOTED_USERNAME);
        database.initialize();

        assertNotEquals(QUOTED_PASSWORD, storedPassword(QUOTED_USERNAME));
        assertEquals(hashBefore, storedPassword(QUOTED_USERNAME));
    }

    @Test
    void returnsGenericErrorAndLogsInternalDetails() throws Exception {
        AuthResource resource = new AuthResource();
        AuthService failingAuthService = new AuthService() {
            @Override
            public LoginResponse login(LoginRequest request) {
                throw new IllegalStateException("Test-only internal detail");
            }
        };
        Field authServiceField = AuthResource.class.getDeclaredField("authService");
        authServiceField.setAccessible(true);
        authServiceField.set(resource, failingAuthService);

        Logger authLogger = Logger.getLogger(AuthResource.class.getName());
        List<LogRecord> logRecords = new ArrayList<>();
        Handler testHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logRecords.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        authLogger.addHandler(testHandler);
        Response response;
        try {
            response = resource.login(new LoginRequest());
        } finally {
            authLogger.removeHandler(testHandler);
        }

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        Map<?, ?> body = (Map<?, ?>) response.getEntity();
        assertEquals("Internal authentication error", body.get("message"));
        assertEquals("InternalServerError", body.get("exception"));
        assertFalse(body.toString().contains("Test-only internal detail"));
        assertFalse(body.toString().contains(IllegalStateException.class.getName()));
        assertTrue(logRecords.stream().anyMatch(record ->
                record.getThrown() instanceof IllegalStateException
                        && "Test-only internal detail".equals(record.getThrown().getMessage())));
    }

    private String storedPassword(String username) throws Exception {
        try (Connection connection = DriverManager.getConnection(TEST_DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString("password");
            }
        }
    }

    private io.restassured.response.Response loginFromOrigin(String origin) {
        return given()
                .header("Origin", origin)
                .contentType("application/json")
                .body("{\"username\":\"missing-user\",\"password\":\"WrongPassword\"}")
                .when().post("/auth/login")
                .then()
                .extract().response();
    }

    private String validClaimsJson() {
        long now = Instant.now().getEpochSecond();
        return "{\"sub\":\"student\",\"role\":\"STUDENT\",\"iat\":" + (now - 1)
                + ",\"exp\":" + (now + 60) + "}";
    }

    private String signTestJwt(String headerJson, String claimsJson) throws GeneralSecurityException {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedHeader = encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedClaims = encoder.encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedClaims;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                System.getenv("AUTH_TOKEN_SECRET").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = encoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }
}
