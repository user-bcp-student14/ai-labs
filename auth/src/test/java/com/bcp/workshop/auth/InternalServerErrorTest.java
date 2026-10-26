package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@QuarkusTestResource(AuthDatabaseTestResource.class)
@TestProfile(InternalServerErrorTest.FailingAuthenticationProfile.class)
class InternalServerErrorTest {
    @Test
    void returnsGenericInternalServerErrorWithoutExposingExceptionDetails() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .body("message", is("Internal authentication error"))
                .body("exception", is("InternalServerError"))
                .body(not(containsString("Test-only internal detail")));
    }

    public static class FailingAuthenticationProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.arc.selected-alternatives",
                    FailingAuthService.class.getName());
        }
    }
}
