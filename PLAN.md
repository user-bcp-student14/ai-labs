# Plan de corrección — auth

## Alcance y estado

Este plan cubre los hallazgos seleccionados `H-AUTH-01` a `H-AUTH-08`. La
selección inicial fue una propuesta; la aprobación humana se registra al final
de este archivo. Las ejecuciones posteriores y sus resultados se conservan en
la sección de historial, sin alterar la secuencia originalmente propuesta.

`docs/ENDPOINTS.md` no existe. Por ello, el punto de partida es el
comportamiento observado en `auth/`, no un contrato acordado: `POST
/auth/login` consume y produce JSON; las solicitudes nulas o sin username o
password devuelven 400; credenciales sin coincidencia devuelven 401; el éxito
devuelve `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y
`debug`. En la respuesta de éxito actual `expiresIn` es 3600. Las pruebas de
caracterización deben registrar estos detalles antes de cambiar código y
mantenerlos salvo decisión humana explícita.

## Secuencia propuesta

1. Crear primero las pruebas de caracterización indicadas en `H-AUTH-08` y
   comprobar el baseline con `mvn test` ejecutado desde `auth/`. El último
   resultado documentado fue un test de éxito solamente; no se presume que
   cubra los casos nuevos.
2. Investigar dependencias en `H-AUTH-07` sin actualizar ni eliminar
   versiones por anticipado.
3. Implementar solo tras revisión y aprobación humana. Separar API, servicio,
   repositorio y seguridad en cambios revisables, como indican
   `AGENTS.md` y `docs/GOOD_PRACTICES.md`.
4. Ejecutar regresión y registrar resultados reales. Mantener visibles los
   fallos y los gates de CodeQL, Dependency Review y JaCoCo; no excluirlos ni
   rebajar umbrales.

## H-AUTH-01 — Contraseñas almacenadas en texto plano
-Aprobado
- **Hallazgo:** `H-AUTH-01` (alta), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** el esquema y las cuentas sembradas conservan contraseñas
  literales; una lectura de la base permitiría obtenerlas directamente.
- **Cambio propuesto:** introducir un hash lento con salt (por ejemplo BCrypt),
  verificar credenciales contra el hash y sembrar nuevas credenciales solo
  como hashes. Diseñar una migración idempotente para bases SQLite ya creadas,
  de modo que las credenciales existentes sigan autenticando durante la
  transición y no queden almacenadas en claro al finalizar. Verificar primero
  si las utilidades de Quarkus disponibles satisfacen la necesidad; justificar
  cualquier dependencia adicional antes de añadirla.
- **Comportamiento a preservar:** mismas credenciales ficticias de laboratorio
  continúan permitiendo login; contraseña incorrecta continúa devolviendo 401;
  formato y campos de las respuestas del endpoint no cambian.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  nueva clase de hash/verificación bajo `auth/src/main/java/com/bcp/workshop/auth/`;
  `auth/pom.xml` solo si se justifica una dependencia; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** probar login válido e inválido contra una base
  SQLite temporal; verificar también la ruta de migración para una base
  temporal con la fila legacy y comprobar que el login válido se conserva.
  Las pruebas deben usar valores ficticios y no imprimir contraseñas.
- **Dependencia/decisión:** acordar estrategia de migración y tratamiento de
  bases persistentes antes de implementar; no hay evidencia de revisión humana
  ni de migración aprobada.

## H-AUTH-02 — SQL de credenciales concatenado

- **Hallazgo:** `H-AUTH-02` (alta), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** username y password se concatenan en el SQL y se ejecuta con
  `Statement`; los caracteres de entrada pueden alterar la sintaxis o hacer
  fallar la consulta.
- **Cambio propuesto:** usar `PreparedStatement` con placeholders y enlazar
  ambos valores con `setString`; mantener la consulta de solo lectura y cerrar
  recursos con try-with-resources.
- **Comportamiento a preservar:** credenciales válidas encuentran al usuario;
  credenciales inválidas siguen dando 401; una comilla que sea parte literal
  de un username se compara como dato y no como sintaxis SQL.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  pruebas de repositorio o endpoint bajo `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** contra SQLite temporal, verificar cuenta válida,
  credenciales inválidas y una cuenta ficticia cuyo username contenga una
  comilla simple. La cuenta con comilla debe encontrarse por su valor literal;
  una consulta con parámetros enlazados debe distinguir este resultado de la
  construcción concatenada actual. Añadir comprobación de respuesta 401 para
  credenciales inválidas.
- **Dependencia/decisión:** ninguna dependencia adicional prevista. Coordinar
  la prueba con H-AUTH-01 para compartir fixture SQLite sin duplicar setup.

## H-AUTH-03 — Token Base64 sin firma criptográfica

- **Hallazgo:** `H-AUTH-03` (alta), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** el generador codifica con Base64 una cadena que contiene
  username, role, expiración y secreto; Base64 no proporciona firma ni
  integridad. El uso y validación por consumidores no se inspeccionó dentro
  del alcance.
- **Cambio propuesto:** reemplazar la generación artesanal por un token
  criptográficamente firmado, con claims explícitos y expiración verificable.
  Extraer la generación/verificación a una responsabilidad de seguridad y
  definir constantes con nombre para las duraciones y claims, sin valores
  mágicos. La clave se obtendrá de configuración externa como se plantea en
  `H-AUTH-04`; nunca se añadirá un secreto real al repositorio.
- **Comportamiento a preservar:** mantener `POST /auth/login`, el JSON de
  respuesta, los campos `token` y `expiresIn`, y la duración observada de 3600
  segundos hasta que exista una decisión explícita distinta. El valor del
  token será opaco para el cliente; su formato interno cambiará.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
  o su reemplazo con nombre apropiado; nueva clase de seguridad/configuración
  bajo `auth/src/main/java/com/bcp/workshop/auth/`; `auth/pom.xml` solo si una
  dependencia de firma es necesaria y está justificada; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** registrar respuesta exitosa y duración; decodificar
  el token actual en una prueba de caracterización aislada para documentar
  claims observados sin convertir el formato legacy en contrato de cliente.
  Antes de refactorizar token, agregar pruebas del token nuevo para firma
  válida, modificación inválida, expiración y claims requeridos.
- **Dependencia/decisión:** identificar consumidores del token fuera de este
  alcance antes de desplegar el cambio de formato; no se han verificado sus
  expectativas ni acordado emisor/audiencia.

## H-AUTH-04 — Secreto literal y configuración CORS permisiva

- **Hallazgo:** `H-AUTH-04` (alta), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** había un secreto literal en Java y CORS declaraba el patrón
  `/.*/`. El secreto ya se externalizó junto con H-AUTH-03. Se aprobaron
  `http://localhost:8080` para desarrollo y `https://localhost:8080` para
  producción.
- **Cambio propuesto:** externalizar el secreto de firma y definir orígenes
  CORS exactos por perfil de Quarkus. En desarrollo usar solo
  `http://localhost:8080`; en producción, solo `https://localhost:8080`, sin
  conservar el fallback permisivo.
- **Comportamiento a preservar:** no cambiar método, ruta ni formato de login.
  Una lista CORS restringida puede cambiar qué orígenes de navegador funcionan;
  los orígenes autorizados deben acordarse antes de implementarla.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
  y/o nueva clase de configuración en `auth/src/main/java/com/bcp/workshop/auth/`;
  `auth/src/main/resources/application.properties`; pruebas de configuración
  bajo `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** comprobar y registrar la configuración actual de
  puerto, host y CORS por perfil; probar resolución de configuración con
  valores ficticios en entorno de test y que la aplicación no use un secreto
  literal como fallback.
- **Dependencia/decisión:** aprobación registrada por Manuel y Developer
  Backend (2026-10-26). Manuel aprobó `http://localhost:8080` para desarrollo
  y `https://localhost:8080` para producción durante la ejecución de este
  cambio.

## H-AUTH-05 — Respuestas de error exponen detalles

- **Hallazgo:** `H-AUTH-05` (media), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** el 500 devuelve mensaje y clase de excepción; el 401 concatena
  el username. El error puede exponer información interna o datos aportados
  por el cliente.
- **Cambio propuesto:** registrar detalles de excepciones solo en logs del
  servidor y responder con mensajes genéricos, sin exponer clases ni
  excepciones. Preservar los estados HTTP y la forma JSON observados cuando
  sea posible.
- **Comportamiento a preservar/decisión necesaria:** hoy los textos de `message`
  y el campo `exception` son observables, y no existe `docs/ENDPOINTS.md`.
  Retirarlos o cambiar sus valores altera el comportamiento observado; antes
  de hacerlo, documentar y aprobar el contrato de error. Hasta entonces, no
  implementar una respuesta incompatible suponiendo que el cuerpo es interno.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  puntos de captura/logging en `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`;
  pruebas bajo `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** comprobar códigos y cuerpos de 400/401; usar una
  dependencia sustituible o prueba unitaria controlada para caracterizar 500
  sin provocar una falla remota. Después, probar que respuestas genéricas no
  contienen mensajes ni nombres de clases internos y que el detalle permanece
  solo en logs.
- **Dependencia/decisión:** definir el cuerpo de error aprobado antes de
  modificar valores o campos de respuesta; mantener logging sin contraseñas,
  tokens ni secretos.

## H-AUTH-06 — Responsabilidades mezcladas en AuthResource

- **Hallazgo:** `H-AUTH-06` (media), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** `AuthResource.login` valida, accede a persistencia, crea token,
  arma la respuesta y maneja excepciones con condicionales anidados.
- **Cambio propuesto:** separar por responsabilidades siguiendo
  Resource (HTTP) → Service (reglas de negocio) → Repository (persistencia),
  con clases de seguridad para hash/token y DTO de salida que no exponga la
  entidad. Cada método debe hacer una cosa, usar guard clauses, evitar
  duplicación y anidamiento profundo, y nombrar constantes para valores como
  la duración del token.
- **Comportamiento a preservar:** todos los estados, campos y valores de
  respuesta observados por las pruebas de caracterización, salvo cambios de
  error que se aprueben para H-AUTH-05. Mantener el contrato de `POST
  /auth/login`.
- **Archivos previstos:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  nuevas clases Service, Repository, DTO y seguridad bajo
  `auth/src/main/java/com/bcp/workshop/auth/`; pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización previa:** completar en H-AUTH-08 pruebas de request nulo,
  campos ausentes/vacíos, credenciales válidas/inválidas y esquema de éxito;
  comprobar 500 por una dependencia sustituible. Ejecutarlas antes de separar
  capas y volver a ejecutarlas después.
- **Dependencia/decisión:** implementar en etapas revisables, sin refactorizar
  persistencia y token antes de tener pruebas de caracterización, según
  `AGENTS.md`.

## H-AUTH-07 — Verificación SCA de dependencias

- **Hallazgo:** `H-AUTH-07` (media), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** `pom.xml` declara Quarkus `3.27.4` y Commons Text `1.9`; la
  declaración por sí sola no confirma ni descarta advisories aplicables.
- **Cambio propuesto:** obtener inventario del árbol efectivo y consultar
  advisories/fuentes de SCA. Actualizar o remover una dependencia únicamente
  si la evidencia confirma necesidad, seleccionando una versión compatible y
  dejando los quality gates habilitados.
- **Comportamiento a preservar:** endpoints y respuestas no cambian por una
  actualización de dependencia; ejecutar regresión tras cualquier cambio.
- **Archivos previstos:** `auth/pom.xml` solo si un advisory comprobado exige
  actualizar o remover; no se prevé tocar código para una revisión sin
  hallazgo confirmado.
- **Caracterización previa:** guardar baseline de `mvn dependency:tree` y
  resultado de la fuente/scanner aprobado antes del cambio. Luego ejecutar
  `mvn test` y las verificaciones SCA disponibles; registrar cada resultado,
  fecha y versión analizada. No marcar como ejecutado hasta correrlo.
- **Dependencia/decisión:** la consulta de advisories y herramientas de CI
  puede requerir acceso externo/autorización; este plan no confirma CVEs ni
  actualizaciones.

## H-AUTH-08 — Caracterización del contrato y casos de borde

- **Hallazgo:** `H-AUTH-08` (baja), en estado Pendiente según `HALLAZGOS.md`.
- **Problema:** inicialmente la prueba solo cubría login exitoso y validaba
  username y presencia de token; tampoco fijaba el comportamiento CORS.
- **Cambio propuesto:** ampliar `AuthResourceTest` con pruebas de contrato y
  pruebas aisladas para repositorio/token/configuración según H-AUTH-01 a
  H-AUTH-07. Mantener fixtures temporales, valores ficticios y aserciones de
  comportamiento, no payloads de explotación.
- **Comportamiento a preservar:** 200 y campos de éxito actuales; 400 para
  request nulo/campos requeridos vacíos; 401 para credenciales inválidas;
  respuesta JSON observada, incluida la duración `expiresIn`. Registrar el
  contenido de error actual antes de cualquier decisión de sanitización.
- **Archivos previstos:** `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`;
  nuevas pruebas en `auth/src/test/java/com/bcp/workshop/auth/` para repositorio,
  hash, token y configuración si son necesarias. No se prevé tocar producción
  por este hallazgo aislado.
- **Caracterización previa:** escribir y ejecutar pruebas para credenciales
  válidas e inválidas, request nulo y campos vacíos, contenido completo de
  éxito, username con comilla contra SQLite temporal, y respuestas de error
  controladas. Las pruebas de migración, token firmado y configuración se
  incorporan antes de modificar sus respectivas implementaciones.
- **Validación posterior:** desde `auth/`, ejecutar `mvn test`; para meta de
  cobertura del código modificado, ejecutar el flujo de JaCoCo y registrar
  medida y método. El resultado previo de un único test no cubre estos casos.

## Ejecuciones posteriores — H-AUTH-04 y H-AUTH-08

Fecha: 2026-10-26. La aprobación de los IDs consta en la sección anterior.

- Antes del cambio CORS, desde `auth/`, `mvn test`: **pasó**, 25 pruebas,
  0 fallos, 0 errores.
- Se añadieron primero pruebas HTTP para el origen aprobado
  `http://localhost:8080` y para `https://unapproved.example`. Contra la
  configuración previa `/.*/`, el comando siguiente **falló como se esperaba**,
  2 pruebas, 1 fallo: el origen no aprobado recibió
  `Access-Control-Allow-Origin`.

  ```text
  mvn -Dtest=AuthResourceTest#allowsCorsForApprovedOrigin+doesNotAllowCorsForUnapprovedOrigin test
  ```
- Después se sustituyó el comodín por el origen local aprobado y se configuró
  `%prod.quarkus.http.cors.origins=${AUTH_CORS_ORIGINS}` sin fallback. La
  primera repetición encontró que Quarkus responde 403 al origen denegado; se
  ajustó la aserción para caracterizar ese comportamiento. La repetición del
  mismo comando pasó: **2 pruebas, 0 fallos, 0 errores**.
- La regresión final, `mvn verify` desde `auth/`, **pasó**: 27 pruebas,
  0 fallos, 0 errores. JaCoCo generó el informe configurado en
  `auth/target/site/jacoco/`; el informe agregado registró 187/197 líneas
  cubiertas (94,9%). No se modificaron clases Java de producción en esta
  ejecución; la cobertura agregada no se atribuye solo a la configuración.
- Estado: H-AUTH-08 queda Resuelto para la configuración local aprobada.
  H-AUTH-04 queda parcialmente Pendiente: falta la lista de producción y no se
  ejecutó el perfil `prod`; no se afirma que su arranque o CORS de producción
  hayan sido probados. CodeQL y Dependency Review remotos no se ejecutaron.
  Este estado refleja la respuesta inicial sin origen de producción; la
  actualización posterior registra la aprobación recibida y el cambio de
  estado.

### Actualización tras aprobación del origen de producción

- Manuel aprobó `https://localhost:8080` para producción. La configuración
  ahora fija `http://localhost:8080` en el perfil de desarrollo y
  `https://localhost:8080` en `%prod`; no conserva `/.*/` ni requiere un
  fallback de entorno.
- Se añadió `ProductionCorsConfigurationTest` con el perfil `prod`: el origen
  HTTPS aprobado recibe `Access-Control-Allow-Origin`; el origen HTTP de
  desarrollo recibe 403 y no recibe ese header. `mvn -Dtest=ProductionCorsConfigurationTest test`
  desde `auth/`: **pasó**, 2 pruebas, 0 fallos, 0 errores.
- La primera regresión completa falló al iniciar `AuthDatabaseTestResource`
  después de cerrar el perfil `prod`: SQLite no estaba registrado en
  `DriverManager`. El recurso ahora registra su instancia del driver al iniciar
  y la libera al detenerse; no se cambió código de producción por este ajuste.
- Regresión final, `mvn verify` desde `auth/`: **pasó**, 29 pruebas, 0 fallos,
  0 errores. JaCoCo registró 187/197 líneas (94,9%) agregadas en auth. No se
  modificaron clases Java de producción en esta ejecución.
- Estado actualizado: H-AUTH-04 y H-AUTH-08 quedan Resueltos en código y
  pruebas locales, incluidos ambos perfiles. Esto no acredita despliegue ni
  controles remotos; CodeQL y Dependency Review siguen no ejecutados.

## Documentación del contrato — H-AUTH-04 y H-AUTH-08

Fecha: 2026-10-26. Se creó `docs/ENDPOINTS.md` solo con el contrato verificado
de auth; no se modificó código, configuración ni pruebas.

- Desde `auth/`, `mvn -DskipTests package`: **BUILD SUCCESS**. Este comando no
  generó el runner Quarkus usado para la observación local; por ello se ejecutó
  también `mvn -DskipTests quarkus:build`: **BUILD SUCCESS**. Ambos comandos
  omitieron la ejecución de pruebas.
- Se inició el runner local con `AUTH_TOKEN_SECRET` ficticio y una base SQLite
  aislada bajo `auth/target/`; los archivos temporales de la observación se
  eliminaron al detener el proceso.
- Respuestas observadas: `GET /q/health` devolvió 200 y
  `{"status":"UP","checks":[]}`; login válido devolvió 200; credenciales
  inválidas devolvieron 401; request vacío y username ausente devolvieron 400
  con `message`; JSON malformado devolvió 400 sin cuerpo.
- También se observaron 415 para `Content-Type: text/plain`, 406 para
  `Accept: text/plain`, 405 para `GET /auth/login` y 403 sin cuerpo al enviar
  un `Origin` no permitido a login y health.
- Estas fueron comprobaciones HTTP locales de documentación, no una ejecución
  de `mvn test` ni una validación de despliegue. Los ejemplos dinámicos de JWT
  se representan con un marcador y no se guardó ningún token.

## Aprobación y pendientes
- H-AUTH-01
- H-AUTH-02
- H-AUTH-03
- H-AUTH-04
- H-AUTH-05
- H-AUTH-06
- H-AUTH-07
- H-AUTH-08
— Aprobado por: Manuel y Developer Backend;
fecha: 2026-10-26

## Pruebas documentadas de endpoints — H-AUTH-04 y H-AUTH-08

Fecha: 2026-10-26. Se creó `docs/TESTING.md` de acuerdo con los endpoints y las
respuestas registrados en `docs/ENDPOINTS.md`. No se cambió código de
producción.

- Se completaron pruebas HTTP de health, request vacío/JSON malformado,
  negociación de contenido, método no permitido y errores CORS en los perfiles
  de desarrollo y producción.
- Se añadió `InternalServerErrorTest` para inducir una excepción mediante un
  servicio CDI alternativo exclusivo del perfil de prueba y comprobar por HTTP
  el `500` genérico sin filtrar el detalle. El test unitario previo que invocaba
  `AuthResource` directamente se conserva.
- Desde `auth/`, `mvn -Dtest=InternalServerErrorTest test`: **pasó**, 1 prueba,
  0 fallos y 0 errores.
- Desde `auth/`, `mvn verify`: **pasó**, 42 pruebas, 0 fallos, 0 errores y 0
  omitidas. JaCoCo 0.8.12 informó 187/197 líneas cubiertas (94,9%, 95%
  redondeado) en el agregado de clases de producción auth; ramas: 72/94 (76%).
  El reporte está en `auth/target/site/jacoco/index.html`.
- El caso de health `DOWN` sigue pendiente porque el servicio no registra
  health checks propios. Las pruebas CORS ejercitan headers HTTP, no navegador
  ni preflight. CodeQL y Dependency Review no se ejecutaron localmente.

### Repetición posterior de `mvn verify`

- La ejecución solicitada después de documentar las pruebas falló con 42
  pruebas y un fallo: `AuthResourceTest.passwordMigrationIsIdempotent` capturó
  el password fixture en texto plano antes de ejecutar la primera migración.
- Se corrigió la prueba para migrar primero, capturar el hash y comprobar que
  una segunda inicialización conserva ese hash. No se modificó código de
  producción.
- Desde `auth/`, `mvn -Dtest=AuthResourceTest#passwordMigrationIsIdempotent test`:
  **pasó**, 1 prueba, 0 fallos y 0 errores.
- Desde `auth/`, la repetición final de `mvn verify`: **pasó**, 42 pruebas,
  0 fallos, 0 errores y 0 omitidas. El fallo inicial se conserva como evidencia,
  no se presenta como una ejecución exitosa.
