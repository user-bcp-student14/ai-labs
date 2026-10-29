# Tasks

## 1. Pruebas de caracterización

- [x] 1.1 Revisar `AuthResourceTest`, `ProductionCorsConfigurationTest` e `InternalServerErrorTest` contra `docs/ENDPOINTS.md`; las pruebas existentes ya cubren request, respuesta exitosa, errores documentados y CORS de `POST /auth/login`. Verificación agregada: `mvn test` desde `auth/`, 46 pruebas, 0 fallos, 0 errores.
- [x] 1.2 Agregar pruebas con el formato Base64 legacy `username|role|expiry|secret` y con un token firmado con otra clave; ambos se rechazan, conservando las pruebas existentes de token válido, firma modificada y expiración. Verificado en `AuthResourceTest` (39 pruebas).
- [x] 1.3 Caracterizar configuración de secreto ausente y demasiado corto, y comprobar que el secreto sintético no aparece en respuesta ni logs. Verificado en `AuthResourceTest` (39 pruebas), sin imprimir el valor del fixture.

## Evidencia de ejecución

- `mvn test` desde la raíz: no ejecutó pruebas porque no hay `pom.xml` en la raíz.
- `mvn test` desde `auth/`: 42 pruebas; 3 errores de arranque Quarkus por el puerto 8081 ocupado por un proceso preexistente.
- `mvn test` desde `auth/` (repetición con el puerto disponible): 46 pruebas, 0 fallos, 0 errores, 0 omitidas; `BUILD SUCCESS`.
- `mvn "-Dquarkus.http.test-port=0" "-Dtest=AuthResourceTest" test` desde `auth/`: 39 pruebas, 0 fallos, 0 errores.
- `mvn "-Dquarkus.http.test-port=0" "-Dquarkus.http.port=0" "-Dtest=ProductionCorsConfigurationTest" test` desde `auth/`: 4 pruebas, 0 fallos, 0 errores.
- `mvn "-Dquarkus.http.test-port=0" "-Dtest=InternalServerErrorTest" test` desde `auth/`: 1 prueba, 0 fallos, 0 errores.
- `mvn "-Dquarkus.http.test-port=0" "-Dquarkus.http.port=0" test` desde `auth/`: 46 pruebas; 1 error de arranque al cambiar de perfil, donde `AuthResourceTest.rejectsInvalidJwtAlgorithm` intentó usar el puerto 8081 ocupado.

## 2. Implementación incremental en auth

- [x] 2.1 Comparar la emisión actual con la spec: no se encontró brecha; `JwtTokenService` ya emite HS256 con `sub`, `role`, `iat`, `exp` y TTL de 3600 segundos. Verificado por las pruebas de claims/respuesta de login en `AuthResourceTest` y `mvn verify` desde `auth/`.
- [x] 2.2 Comparar la validación actual con la spec: no se encontró brecha; las pruebas cubren firma modificada, clave distinta, entrada malformada, expiración y rechazo del formato legacy con tiempo determinista cuando aplica. Verificado en `AuthResourceTest` y `mvn verify` desde `auth/`.
- [x] 2.3 Revisar la gestión del secreto: `application.properties` lo obtiene de `AUTH_TOKEN_SECRET`, el servicio rechaza valores ausentes/cortos y la prueba confirma que el valor sintético no se revela. Verificado en `AuthResourceTest` y `mvn verify` desde `auth/`; no se usó ningún secreto real.

## 3. Validación del componente

- [x] 3.1 Ejecutar `mvn verify` desde `auth/`: 46 pruebas, 0 fallos/errores/omitidas; `BUILD SUCCESS`. JaCoCo reporta 186/196 líneas (94,9%) en el componente; no se modificó código de producción. Las pruebas HTTP existentes conservan el contrato de `docs/ENDPOINTS.md`.
- Evidencia adicional: `mvn verify` desde `auth/` (con 8081 disponible) terminó con 46 pruebas, 0 fallos, 0 errores, 0 omitidas y JaCoCo 186/196 líneas (94,9%).