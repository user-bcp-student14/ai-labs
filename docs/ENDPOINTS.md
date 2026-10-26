# Contrato HTTP — auth

Este contrato describe el componente `auth/` en el código actual. Los ejemplos
de errores generados por Quarkus reflejan la ejecución local de Quarkus 3.27.4;
los cambios de versión pueden cambiar esos cuerpos.

## GET /q/health

### 1. Qué hace

Publica el estado agregado del servicio para que un consumidor compruebe si el
proceso responde. Actualmente no comprueba conectividad a SQLite.

### 2. Autenticación

No requiere autenticación. No se envía header `Authorization`.

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| — | — | no | No recibe parámetros ni cuerpo. `Accept: application/json` se usa para solicitar la representación JSON. |

Ejemplo HTTP completo; este `GET` no tiene cuerpo JSON:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json
```

### 4. Response exitosa

**Estado:** `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| `status` | string | Estado agregado. En la ejecución observada fue `UP`. |
| `checks` | array | Checks registrados por la extensión. Está vacío en el servicio actual, que no registra checks propios; no hay una forma de elemento que documentar en el código actual. |

Ejemplo observado al ejecutar el servicio:

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | cuerpo observado |
|---|---|---|
| `403 Forbidden` | La solicitud de navegador incluye un header `Origin` fuera de la allowlist del perfil activo. | Sin cuerpo. |

No se observó otro error HTTP de este endpoint: no hay checks de salud propios
que actualmente puedan marcar el servicio como `DOWN`. Si el proceso no está
activo, el cliente no recibe un código HTTP; obtiene un error de conexión.
La respuesta si en el futuro se registra un health check que falle queda como
pregunta abierta, porque no está implementada en `auth/`.

### 6. Notas de seguridad

- El endpoint es público y no entrega datos de usuarios ni del estado de la
  base de datos en la respuesta observada.
- CORS permite `http://localhost:8080` fuera del perfil `prod` y
  `https://localhost:8080` en el perfil `prod`. CORS es una política del
  navegador; no sustituye autenticación.

**Evidencia:** extensión `quarkus-smallrye-health` en `auth/pom.xml:40`; CORS
por perfil en `auth/src/main/resources/application.properties:4-6`. Respuesta
`200` y rechazo CORS observados localmente el 2026-10-26.

## POST /auth/login

### 1. Qué hace

Autentica a un usuario del laboratorio y, si las credenciales son válidas,
devuelve sus datos básicos junto con un JWT firmado para que el cliente pueda
presentarlo a los servicios que lo consuman.

### 2. Autenticación

No requiere autenticación previa ni header `Authorization`. El cliente envía
las credenciales en el cuerpo JSON.

### 3. Request

**Content-Type:** `application/json`.
**Accept:** debe permitir `application/json`; `Accept: text/plain` devuelve
`406 Not Acceptable`.

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | sí | Nombre de usuario. No puede ser nulo, vacío ni contener solo espacios. |
| `password` | string | sí | Contraseña. No puede ser nula, vacía ni contener solo espacios. |
| `client` | string | no | Campo aceptado por el DTO; el servicio de autenticación actual no lo utiliza. |

Ejemplo JSON completo con credenciales ficticias del laboratorio:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4. Response exitosa

**Estado:** `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| `token` | string | JWT firmado con HS256; contiene los claims `sub`, `role`, `iat` y `exp`. Su valor cambia en cada emisión. |
| `expiresIn` | integer | Duración del token en segundos: `3600`. |
| `user` | object | Datos básicos del usuario autenticado. |
| `user.username` | string | Nombre de usuario autenticado. |
| `user.displayName` | string | Nombre para mostrar; se escapa como HTML antes de incluirlo en la respuesta. |
| `user.role` | string | Rol asignado a la cuenta. |
| `debug` | string | Indicador fijo de flujo actual: `authenticated-with-legacy-flow`. |

Ejemplo JSON completo. El token se representa con un marcador porque es un
valor bearer dinámico; no se debe copiar ni reutilizar este marcador:

```json
{
  "token": "<JWT HS256 dinámico>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | cuerpo de respuesta |
|---|---|---|
| `400 Bad Request` | Cuerpo JSON `null` o cuerpo HTTP vacío; el recurso recibe un request nulo. | `{"message":"Request is null"}` |
| `400 Bad Request` | `username` ausente, nulo, vacío o compuesto solo por espacios. | `{"message":"Username is required"}` |
| `400 Bad Request` | `password` ausente, nula, vacía o compuesta solo por espacios. | `{"message":"Password is required"}` |
| `400 Bad Request` | JSON malformado antes de invocar el recurso. | Sin cuerpo en la ejecución observada de Quarkus. |
| `401 Unauthorized` | No existe coincidencia para el usuario y la contraseña. Un error SQL de búsqueda también se convierte actualmente en usuario no encontrado. | `{"message":"Invalid username or password"}` |
| `403 Forbidden` | El header `Origin` no coincide con el origen permitido por el perfil activo. | Sin cuerpo. |
| `405 Method Not Allowed` | Se usa otro método HTTP en `/auth/login`, por ejemplo `GET`. | Sin cuerpo en la ejecución observada de Quarkus. |
| `406 Not Acceptable` | El cliente solicita un tipo de respuesta incompatible, por ejemplo `Accept: text/plain`. | Sin cuerpo en la ejecución observada de Quarkus. |
| `415 Unsupported Media Type` | El cuerpo no se envía como `application/json`, por ejemplo `Content-Type: text/plain`. | Sin cuerpo en la ejecución observada de Quarkus. |
| `500 Internal Server Error` | Ocurre una excepción inesperada durante el procesamiento de autenticación. | `{"message":"Internal authentication error","exception":"InternalServerError"}` |

Los errores de validación de campos y de credenciales son respuestas definidas
por `auth/`. Los errores de JSON malformado, método, negociación de respuesta
y media type son generados por Quarkus y sus cuerpos deben volver a
caracterizarse si se actualiza el framework.

### 6. Notas de seguridad

- El JWT se firma con HS256 e incluye `sub`, `role`, `iat` y `exp`; la
  expiración se verifica realmente y el TTL es de 3600 segundos. No incluye
  claims `iss` ni `aud`.
- La clave se carga desde `AUTH_TOKEN_SECRET`; debe tener al menos 32 bytes
  UTF-8. No enviar ni registrar esa clave.
- La respuesta 500 expone solo mensajes genéricos y el código fijo
  `InternalServerError`; el detalle de la excepción queda en el log del
  servidor.
- La autenticación no requiere `Authorization`, y este componente no expone
  aquí un endpoint de verificación del JWT. El consumidor debe validar firma,
  expiración y claims antes de confiar en el token.
- `client` se acepta pero no altera la autenticación. `debug` forma parte de
  la respuesta actual; no se debe usar como claim de autorización.
- CORS permite `http://localhost:8080` fuera del perfil `prod` y
  `https://localhost:8080` en `prod`; un origen no permitido recibe `403`.

**Evidencia:** ruta y estados en `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15-48`;
validaciones y armado de la respuesta en
`auth/src/main/java/com/bcp/workshop/auth/AuthService.java:21-50`;
campos del request en `auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-5`;
campos de salida en `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4`;
firma, claims, TTL y secreto en
`auth/src/main/java/com/bcp/workshop/auth/JwtTokenService.java:23-56`;
resultado de búsqueda ante fallo SQL en
`auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:66-90`;
errores CORS por perfil en
`auth/src/main/resources/application.properties:4-6`.

## Preguntas abiertas

- Si se agregan health checks, ¿qué check concreto debe determinar el estado
  `DOWN` y qué contrato de error debe consumir el orquestador?
- ¿Debe conservarse `debug` en la respuesta pública de login a largo plazo?
