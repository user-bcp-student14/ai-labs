# Proposal

## Why

GitHub Issue [#14 — MCP - Local HU-01: Token de autenticación seguro](https://github.com/user-bcp-student14/workshop-auth-backend-archive-taller-anterior/issues/14) identifica el uso previo de tokens reversibles sin firma y exige que la emisión y validación compartan un contrato seguro. Este cambio formaliza ese contrato para `auth/`, evita aceptar tokens manipulables o expirados y protege el secreto de firma.

## What Changes

- **Dentro del alcance (`auth/`)**: especificar que los tokens de login sean JWT firmados con HS256, incluyan `sub`, `role`, `iat` y `exp`, mantengan el TTL de 3600 segundos y rechacen tokens con firma inválida, expirados o en formato legado.
- **Dentro del alcance (`auth/`)**: especificar el uso de `AUTH_TOKEN_SECRET` como configuración externa, sin incorporar ni revelar su valor.
- **Comportamiento que se preserva**: `POST /auth/login` conserva el contrato de `docs/ENDPOINTS.md`, incluidos request, estados, campos de respuesta, `expiresIn: 3600`, usuario y `debug`. Solo el valor dinámico de `token` queda sujeto a las garantías de seguridad descritas.
- **Fuera del alcance**: cambios en `api/` o `web/`, actualización del consumidor Python mencionado por el Issue, modificación de CORS (H-05) y cambios en rutas, payloads o duración del login. La coordinación del consumidor queda como dependencia de integración, sin ampliar aquí el código ni las pruebas fuera de `auth/`.

## Capabilities

### New Capabilities
- `auth-token`: contrato observable para emisión y validación de tokens del componente de autenticación.

### Modified Capabilities
- Ninguna. `openspec/specs/` no contiene capacidades previas.

## Impact

- Componente elegido: `auth/`, en particular el comportamiento del token usado por `POST /auth/login` y su validación.
- Baseline HTTP: `docs/ENDPOINTS.md`; el contrato de login no cambia.
- El código actual de `auth/` ya refleja JWT HS256, TTL de 3600 segundos y configuración `AUTH_TOKEN_SECRET`; la fase de implementación deberá contrastar cada escenario con ese estado y atender solo brechas comprobadas.
- Issue #14 también menciona el consumidor Python. Sus archivos y pruebas no forman parte de este cambio acotado y requerirán coordinación separada para completar el corte emisor-consumidor.