# Pruebas de endpoints — auth

La matriz corresponde únicamente a los endpoints de `auth/` documentados en
[ENDPOINTS.md](./ENDPOINTS.md). Los datos de usuario y la clave configurada por
Maven son fixtures de prueba; no son credenciales de producción.

## Matriz de casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Consulta sin `Authorization` | Camino feliz | `200`; `status` es `UP`. |
| `GET /q/health` | Lista de checks vacía en el servicio actual | Caso borde | `200`; `checks` es `[]`. |
| `GET /q/health` | Origen CORS no permitido, perfil test/desarrollo | Error | `403`, cuerpo vacío y sin `Access-Control-Allow-Origin`. |
| `GET /q/health` | Origen de desarrollo rechazado con perfil `prod` | Error | `403`, cuerpo vacío y sin `Access-Control-Allow-Origin`. |
| `GET /q/health` | Sin `Origin` con perfil `prod` | Camino feliz | `200`; `status` es `UP` y `checks` es `[]`. |
| `POST /auth/login` | Credenciales ficticias válidas, con `client` | Camino feliz | `200`; usuario y rol esperados, JWT no nulo, `expiresIn` es `3600` y `debug` conserva el valor documentado. |
| `POST /auth/login` | Credenciales válidas con username que contiene comilla | Caso borde | `200`; username devuelto literalmente, sin alterar la búsqueda. |
| `POST /auth/login` | Username y password omitidos individualmente | Caso borde/error de validación | `400`; mensaje específico `Username is required` o `Password is required`. |
| `POST /auth/login` | Username o password compuesto solo por espacios | Caso borde/error de validación | `400`; mensaje específico de campo requerido. |
| `POST /auth/login` | Cuerpo JSON `null` o cuerpo HTTP vacío | Error | `400`; `{"message":"Request is null"}`. |
| `POST /auth/login` | JSON malformado | Error | `400`; cuerpo vacío, según Quarkus 3.27.4. |
| `POST /auth/login` | Usuario/contraseña no coinciden | Error | `401`; `{"message":"Invalid username or password"}`. |
| `POST /auth/login` | Origen CORS aprobado en desarrollo | Camino feliz de CORS | La respuesta de credenciales inválidas es `401` y contiene `Access-Control-Allow-Origin: http://localhost:8080`. |
| `POST /auth/login` | Origen CORS no permitido en desarrollo | Error | `403`, cuerpo vacío y sin `Access-Control-Allow-Origin`. |
| `POST /auth/login` | Origen HTTPS aprobado en perfil `prod` | Camino feliz de CORS | La respuesta de credenciales inválidas es `401` y contiene `Access-Control-Allow-Origin: https://localhost:8080`. |
| `POST /auth/login` | Origen HTTP de desarrollo en perfil `prod` | Error | `403`, cuerpo vacío y sin `Access-Control-Allow-Origin`. |
| `POST /auth/login` | `Content-Type: text/plain` | Error | `415`, cuerpo vacío. |
| `POST /auth/login` | `Accept: text/plain` | Error | `406`, cuerpo vacío. |
| `POST /auth/login` | `GET /auth/login` | Error | `405`, cuerpo vacío. |
| `POST /auth/login` | Excepción interna inducida por fixture CDI de pruebas | Error | `500`; mensaje genérico y `InternalServerError`, sin el detalle de la excepción en la respuesta. |

Los casos HTTP y sus aserciones ejecutables están en
[AuthResourceTest.java](../auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java),
[ProductionCorsConfigurationTest.java](../auth/src/test/java/com/bcp/workshop/auth/ProductionCorsConfigurationTest.java)
y [InternalServerErrorTest.java](../auth/src/test/java/com/bcp/workshop/auth/InternalServerErrorTest.java).

## Código de los tests

Los siguientes métodos son el código ejecutable que cubre los casos HTTP
anteriores. Las pruebas de estado 500 usan el servicio alternativo
[FailingAuthService.java](../auth/src/test/java/com/bcp/workshop/auth/FailingAuthService.java)
seleccionado solo por el perfil de
[InternalServerErrorTest.java](../auth/src/test/java/com/bcp/workshop/auth/InternalServerErrorTest.java).

```java
// AuthResourceTest.java
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
```

```java
// ProductionCorsConfigurationTest.java: los cuatro métodos se ejecutan con perfil prod.
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
```

```java
// InternalServerErrorTest.java
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
```

## Ejecuciones y cobertura

Todas las pruebas se ejecutaron con el directorio de trabajo `auth/`.

| comando | directorio | código de salida | resultado |
|---|---|---:|---|
| `mvn -Dtest=InternalServerErrorTest test` | `auth/` | `0` | 1 prueba, 0 fallos, 0 errores, 0 omitidas. |
| `mvn verify` (primera repetición) | `auth/` | `1` | 42 pruebas; falló `AuthResourceTest.passwordMigrationIsIdempotent` porque capturó el fixture aún en texto plano antes de invocar la migración. |
| `mvn -Dtest=AuthResourceTest#passwordMigrationIsIdempotent test` (tras corregir la precondición) | `auth/` | `0` | 1 prueba, 0 fallos, 0 errores, 0 omitidas. |
| `mvn verify` (repetición final) | `auth/` | `0` | 42 pruebas, 0 fallos, 0 errores, 0 omitidas; `BUILD SUCCESS`. |

Medición de cobertura: JaCoCo `0.8.12`, ejecutado por el goal `report` del
plugin configurado en `auth/pom.xml` durante `mvn verify`. El informe
HTML está en `auth/target/site/jacoco/index.html`. El total de clases de
producción de auth registra 187/197 líneas cubiertas, aproximadamente 94,9%
(95% redondeado en el informe), por encima del umbral de 85%. La cobertura de
ramas es 72/94 (76%); este porcentaje no se confunde con la cobertura de líneas
solicitada.

## Fallos, pendientes y pruebas propuestas

- **Fallo observado y corregido:** la primera repetición de `mvn verify`
  encontró que `passwordMigrationIsIdempotent` dependía de que otra prueba ya
  hubiera migrado el fixture. La prueba ahora ejecuta la migración una vez
  antes de capturar el hash y vuelve a ejecutarla para comprobar idempotencia;
  la prueba aislada y la suite completa pasaron después del ajuste. No se
  modificó código de producción.
- **Fallos en la última ejecución:** ninguno.
- **Pendiente:** `GET /q/health` no tiene un health check que pueda fallar; por
  eso no existe una respuesta `DOWN` que probar. La política y el contrato para
  un futuro check quedan como pregunta abierta en `ENDPOINTS.md`.
- **Alcance de CORS:** las pruebas envían `Origin` HTTP directamente; no
  automatizan un navegador real ni un flujo `OPTIONS` de preflight, que no está
  documentado como respuesta del contrato.
- **Pruebas propuestas:** agregar una prueba `DOWN` solo cuando se implemente y
  acuerde un health check concreto; agregar pruebas de preflight si el contrato
  incorpora ese comportamiento.
- **No ejecutado:** CodeQL y Dependency Review requieren controles remotos y no
  se ejecutaron en esta validación local.
