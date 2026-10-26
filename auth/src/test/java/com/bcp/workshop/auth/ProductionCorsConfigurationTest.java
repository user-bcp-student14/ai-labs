package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(AuthDatabaseTestResource.class)
@TestProfile(ProductionCorsConfigurationTest.ProductionProfile.class)
class ProductionCorsConfigurationTest {
    private static final String APPROVED_PRODUCTION_ORIGIN = "https://localhost:8080";
    private static final String DEVELOPMENT_ORIGIN = "http://localhost:8080";
    private static final String ACCESS_CONTROL_ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    @Test
    void allowsOnlyApprovedProductionCorsOrigin() {
        io.restassured.response.Response response = loginFromOrigin(APPROVED_PRODUCTION_ORIGIN);

        assertEquals(401, response.statusCode());
        assertEquals(APPROVED_PRODUCTION_ORIGIN, response.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
    }

    @Test
    void rejectsDevelopmentOriginInProductionProfile() {
        io.restassured.response.Response response = loginFromOrigin(DEVELOPMENT_ORIGIN);

        assertEquals(403, response.statusCode());
        assertEquals("", response.asString());
        assertFalse(response.getHeaders().hasHeaderWithName(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
    }

    @Test
    void reportsHealthAsUpInProductionProfile() {
        io.restassured.response.Response response = given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .extract().response();

        assertEquals("UP", response.jsonPath().getString("status"));
        assertEquals(0, response.jsonPath().getList("checks").size());
    }

    @Test
    void rejectsDevelopmentOriginForHealthInProductionProfile() {
        io.restassured.response.Response response = given()
                .header("Origin", DEVELOPMENT_ORIGIN)
                .when().get("/q/health")
                .then()
                .extract().response();

        assertEquals(403, response.statusCode());
        assertEquals("", response.asString());
        assertFalse(response.getHeaders().hasHeaderWithName(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
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

    public static class ProductionProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "prod";
        }
    }
}
