# Hallazgos

Discovery de `auth/` basado en evidencia del código. Los estados de la matriz
son iniciales `Pendiente`; no implican aprobación, remediación ni verificación
de hipótesis que requieran ejecución o fuentes externas.

## auth

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-02 | auth | SQL de credenciales construido por concatenación | Vulnerabilidad | Los datos del request se incorporaban al SQL y se ejecutaban con `Statement`; una comilla podía romper la consulta. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35`; `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:38-42,73-80` | alta | Resuelto — consulta de usuario con `PreparedStatement` y valor enlazado; la prueba con username con comilla pasa. |
| H-AUTH-01 | auth | Contraseñas almacenadas y comparadas en texto plano | Vulnerabilidad | La base y las cuentas sembradas conservaban contraseñas literales, exponiéndolas si se leía la base. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:63-66,93-114`; `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:13-70` | alta | Resuelto — hash PBKDF2 con salt y migración idempotente de contraseñas legacy; login de cuentas existentes probado. |
| H-AUTH-03 | auth | Token Base64 con secreto embebido, sin firma criptográfica en su generación | Vulnerabilidad | El token previo era Base64 de claims y secreto concatenados, sin firma ni verificación. Los consumidores fuera de auth no se inspeccionaron. | `auth/src/main/java/com/bcp/workshop/auth/JwtTokenService.java:22-113`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:48-49`; `auth/src/main/resources/application.properties:9` | alta | Resuelto — JWT HS256 con `sub`, `role`, `iat`, `exp`, TTL 3600 y secreto desde entorno; no desplegar hasta verificar consumidores del token. |
| H-AUTH-04 | auth | Secreto literal y configuración CORS permisiva | Vulnerabilidad | El secreto literal anterior ya no se usa. Se reemplazó `/.*/` por orígenes exactos por perfil: `http://localhost:8080` en desarrollo y `https://localhost:8080` en producción. | `auth/src/main/java/com/bcp/workshop/auth/JwtTokenService.java:34-43`; `auth/src/main/resources/application.properties:5-6,10` | alta | Resuelto — secreto externalizado y CORS restringido por perfil; las pruebas verifican el origen permitido y rechazan el otro protocolo con 403, sin `Access-Control-Allow-Origin`. |
| H-AUTH-05 | auth | Respuestas de error exponen datos internos | Vulnerabilidad | El 500 incluía mensaje y clase de excepción; el 401 concatenaba el username, revelando datos no necesarios para el cliente. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:39-48`; prueba en `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:286-329` | media | Resuelto — se conserva status y forma JSON, se sustituyen los valores por mensajes/código genéricos y los detalles de excepción se registran solo en el log. |
| H-AUTH-06 | auth | Resource combina validación, negocio, persistencia y armado de respuesta | Refactor | La ruta HTTP delega ahora la validación y orquestación de login a un servicio; el servicio usa `LegacyDatabase` para persistencia y arma el DTO de salida. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:26-37`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:21-50`; `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:1-4` | media | Resuelto — separación HTTP/servicio/DTO con el comportamiento observado cubierto y JaCoCo al 100% de líneas en las clases modificadas. |
| H-AUTH-07 | auth | Versiones de dependencias candidatas a revisión SCA | Sugerencia | El árbol efectivo confirmaba Quarkus `3.27.4` y Commons Text `1.9`. Commons Text 1.9 está entre las versiones afectadas por CVE-2022-42889 cuando se usa `StringSubstitutor` con entrada no confiable; auth solo usa `StringEscapeUtils.escapeHtml4`. | `auth/pom.xml:13,47-52`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:42` | media | Resuelto — Commons Text actualizado a `1.15.0`; no se encontró uso de `StringSubstitutor` en auth. Quarkus se conserva en `3.27.4`, release LTS que incluye correcciones de seguridad. |
| H-AUTH-08 | auth | Cobertura de pruebas de login inicialmente limitada al caso exitoso | Sugerencia | Se añadieron caracterizaciones para éxito, credenciales inválidas, validaciones, errores 500, password hash/migración, input con comilla, firma/claims/expiración JWT y CORS en perfiles local y producción. | `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:68-85`; `auth/src/test/java/com/bcp/workshop/auth/ProductionCorsConfigurationTest.java:14-37` | baja | Resuelto — ambas configuraciones CORS se prueban por perfil: el origen aprobado recibe el header esperado y el otro se rechaza con 403 y sin `Access-Control-Allow-Origin`. |
| H-AUTH-09 | auth | Siembra de cuentas de laboratorio sin restricción de perfil | Configuración | La siembra de cuentas fijas se controla mediante `auth.seed-lab-users`: habilitada en `dev` y `test`, deshabilitada por defecto y en `prod`. La inicialización no elimina cuentas existentes. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:58-72`; `auth/src/main/resources/application.properties:11-14`; `auth/src/test/java/com/bcp/workshop/auth/DevelopmentSeedConfigurationTest.java:29-42`; `auth/src/test/java/com/bcp/workshop/auth/ProductionCorsConfigurationTest.java:33-45` | media | Resuelto — `python tools/lab.py test auth`: 44 pruebas, 0 fallos; cobertura de líneas de `LegacyDatabase`: 49/53 (92,5%). |
| H-AUTH-10 | auth | Error de consulta de base de datos se interpreta como credenciales inválidas | Manejo de errores | `LegacyDatabase.findByCredentials()` captura `SQLException` y devuelve `null`; `AuthService.login()` trata `null` como credenciales inválidas. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:71-90`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:21-26`; `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:34-41` | media | CONFIRMADOS — el error de persistencia podría responder como 401 en lugar de representar un fallo interno; confirmar el contrato esperado. |
| H-AUTH-11 | auth | Origen CORS de producción fijado a localhost | Configuración | El perfil `prod` declara un único origen `https://localhost:8080`; la prueba de perfil productivo valida ese mismo origen, no uno de despliegue. | `auth/src/main/resources/application.properties:4-6`; `auth/src/test/java/com/bcp/workshop/auth/ProductionCorsConfigurationTest.java:16-26` | media | CONFIRMADOS — confirmar el origen real del navegador cliente antes de concluir incompatibilidad de despliegue. |

Todos los candidatos de auth enumerados en `RETO.md` tienen correspondencia en
código. Para H-AUTH-07, el árbol Maven confirmó las versiones efectivas y se
contrastó Commons Text con el aviso de Apache: la versión previa estaba
afectada bajo uso de `StringSubstitutor` con entrada no confiable, API que no
se encontró en auth. Se actualizó a `1.15.0`. Las notas oficiales de Quarkus
describen `3.27.4` como release LTS de mantenimiento que incluye correcciones
de seguridad; no se atribuye un CVE específico al uso de Quarkus de este
proyecto. CodeQL y Dependency Review remotos no se ejecutaron.

## Ruta observada

Esta sección conserva la evidencia del estado inicial. El flujo se actualizó
para H-AUTH-05 y H-AUTH-06 tras caracterización y aprobación del contrato de
error genérico.

1. **Entrada y validación:** `AuthResource` declara `POST /auth/login` y
   consume/produce JSON. Rechaza request nulo, username nulo/vacío y password
   nulo/vacío con 400; después pasa ambos valores sin normalizarlos a
   `LegacyDatabase.findByCredentials`.
   Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15-35`.
2. **Persistencia:** `LegacyDatabase` toma `AUTH_DB_URL` del entorno o usa
   `jdbc:sqlite:./data/workshop.db`, crea `data/` y la tabla `legacy_users`, y
   siembra dos cuentas de laboratorio. La búsqueda abre SQLite y concatena
   credenciales al SQL. Si no encuentra fila o captura una excepción, devuelve
   `null`; el recurso responde 401 cuando el resultado es nulo.
   Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:15-24,31-43`;
   `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:35-37`.
3. **Token y respuesta:** si encuentra usuario, `AuthResource` llama a
   `LegacyTokenUtil.create(username, role)`. La respuesta 200 contiene `token`,
   `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug`.
   Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:39-49`;
   `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8,12-15`.
4. **Configuración HTTP y errores:** las propiedades declaran puerto 8080,
   host local con host distinto en perfil prod, CORS habilitado, patrón de
   origen `/.*/` y métodos/headers permitidos. El perfil desplegado y el efecto
   de CORS no se probaron. El recurso arma un 500 con mensaje y clase de
   excepción; la inicialización y búsqueda de DB capturan excepciones e
   imprimen stacktrace.
   Evidencia: `auth/src/main/resources/application.properties:1-7`;
   `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:54-58`;
   `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:18-27,39-40`.

Solo el caso exitoso tiene prueba automatizada existente y ejecutada en el
discovery previo: `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:12-21`.
`docs/ENDPOINTS.md` no existe; este recorrido describe el contrato observado
en código, no un contrato acordado.

## Estado de implementación auth

`POST /auth/login` delega desde `AuthResource` a `AuthService`. El servicio
valida el request, consulta `LegacyDatabase` y crea un `LoginResponse` con la
misma forma observada (`token`, `expiresIn`, `user` y `debug`). La
caracterización conserva los estados 400/401/500, el esquema de éxito y las
claves de error. El 401 usa `Invalid username or password`; el 500 usa
`Internal authentication error` y `InternalServerError`, mientras el detalle
de la excepción se registra en el log y no se incluye en el JSON. El campo
`token` contiene ahora un JWT HS256 con claims `sub`, `role`, `iat` y `exp`;
`expiresIn` continúa en 3600. El secreto se configura mediante
`AUTH_TOKEN_SECRET` y requiere al menos 32 bytes UTF-8. El despliegue queda
bloqueado hasta verificar consumidores del token fuera de auth. CORS aún
conserva `/.*/`; no se cambió al haberse diferido la lista de orígenes.
Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:26-48`;
`auth/src/main/java/com/bcp/workshop/auth/AuthService.java:21-50`;
`auth/src/main/java/com/bcp/workshop/auth/JwtTokenService.java:22-174`;
`auth/src/main/resources/application.properties:4-9`;
`auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:1-4`;
`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:21-359`.

## Verificación ejecutada

- En el discovery previo se ejecutó `mvn test` desde `auth/`: **pasó**; 1
  prueba, 0 fallos, 0 errores, 0 omitidas. No cubrió SQL con comillas ni los
  restantes casos del endpoint.
- Esa ejecución no valida advisories, CORS en despliegue, cobertura JaCoCo ni
  comportamiento de integración con otros componentes.
- `docs/TESTING.md` y `PLAN.md` no existían en el discovery previo. Se creó
  `prompts/auth-discovery.md`; no se creó un plan de implementación ni
  documentación completa del contrato de endpoints.
- Tras la aprobación del contrato genérico de H-AUTH-05, la prueba de
  caracterización falló antes del cambio en 2 aserciones: el 401 contenía el
  username y el 500 el mensaje interno de prueba. Después de implementar la
  sanitización, `mvn verify` desde `auth/` pasó con 12 tests, 0 fallos y 0
  errores. JaCoCo midió 100% de líneas en `AuthResource` (15/15),
  `AuthService` (19/19) y `LoginResponse` (2/2).
- Para H-AUTH-07, `mvn dependency:tree` confirmó Commons Text `1.9` y Quarkus
  `3.27.4` antes del cambio. Después, `mvn verify dependency:tree` desde
  `auth/` pasó con 12 tests y confirmó Commons Text `1.15.0`; Quarkus sigue
  en `3.27.4`. CodeQL y Dependency Review remotos no se ejecutaron.
- Para H-AUTH-03, la caracterización del token legacy pasó antes del cambio;
  la nueva aserción JWT falló como se esperaba con el Base64 de un solo
  segmento. Tras reemplazar el generador, `mvn verify` pasó con 25 tests y
  JaCoCo midió 61/65 líneas (93,8%) en `JwtTokenService`, 15/15 en
  `AuthResource`, 19/19 en `AuthService` y 2/2 en `LoginResponse`.
- CodeQL y Dependency Review remotos no se ejecutaron. `docs/ENDPOINTS.md` y
  `docs/TESTING.md` siguen ausentes; no se crearon en este paso.

## Candidatos de discovery auth

Los siguientes hallazgos son candidatos basados en inspección de `auth/`.
No representan aprobación, remediación ni verificación en un entorno de
despliegue. Las pruebas existentes se ejecutaron con
`python tools/lab.py test auth`: 42 tests, 0 fallos, 0 errores; este resultado
no confirma el comportamiento de despliegue señalado en los candidatos.

### Cuentas de laboratorio sembradas con credenciales fijas
- Estado: Resuelto
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:58-72` consulta la configuración antes de sembrar; `auth/src/main/resources/application.properties:11-14` habilita `dev` y `test` y deshabilita `prod`; `auth/src/test/java/com/bcp/workshop/auth/DevelopmentSeedConfigurationTest.java:29-42` y `auth/src/test/java/com/bcp/workshop/auth/ProductionCorsConfigurationTest.java:33-45` validan ambos perfiles.
- Impacto: si este código inicializa una base en un entorno no local, las cuentas de laboratorio podrían quedar disponibles con credenciales conocidas por quienes tengan acceso al código del taller.
- Prioridad propuesta: media; depende de confirmar si la inicialización se usa fuera del laboratorio.
- Contrato potencialmente afectado: autenticación de cuentas y roles de `POST /auth/login`.
- Cómo validarlo: `python tools/lab.py test auth` pasó con 44 pruebas, 0 fallos y 0 errores. JaCoCo midió 49/53 líneas (92,5%) en `LegacyDatabase`. El test `dev` confirma la siembra; el test `prod` confirma cero cuentas nuevas. No se eliminan cuentas existentes.

### Fallo de consulta presentado como credenciales inválidas
- Estado: CONFIRMADOS
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:71-90` convierte `SQLException` en `null`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:21-26` convierte `null` en `InvalidCredentialsException`; `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:34-41` responde 401 para esa excepción.
- Impacto: una indisponibilidad o error de persistencia puede ser indistinguible para el cliente de credenciales incorrectas y producir un estado HTTP que no representa la causa.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: status y cuerpo de error de `POST /auth/login` ante fallos de persistencia.
- Cómo validarlo: provocar un fallo controlado de conexión a la base en una prueba aislada y observar status, cuerpo y logs; contrastar el resultado con el contrato acordado para errores internos.

### Origen CORS de producción fijado a localhost
- Estado: CONFIRMADOS
- Evidencia: `auth/src/main/resources/application.properties:4-6` define los orígenes permitidos; `auth/src/test/java/com/bcp/workshop/auth/ProductionCorsConfigurationTest.java:16-26` prueba únicamente el origen `https://localhost:8080`.
- Impacto: si el navegador cliente se sirve desde otro origen, el navegador bloquearía el acceso cross-origin al endpoint de autenticación en el perfil productivo.
- Prioridad propuesta: media; el origen de despliegue no está documentado en esta evidencia.
- Contrato potencialmente afectado: acceso desde navegador a `POST /auth/login` y preflight CORS en producción.
- Cómo validarlo: confirmar el origen del frontend autorizado para el despliegue y probar desde ese origen la solicitud OPTIONS y el login en una instancia del perfil productivo.
