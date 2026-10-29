# Spec Delta

## Purpose

Define the security and compatibility guarantees for authentication tokens issued and validated by the auth component, without changing the documented login HTTP contract.

## ADDED Requirements

### Requirement: Login preserves its documented HTTP contract
Successful and unsuccessful login requests MUST retain the request, response, and error behavior documented in `docs/ENDPOINTS.md`.

#### Scenario: Successful login keeps the response shape
- **WHEN** a client submits valid credentials to `POST /auth/login`, with or without the optional `client` field
- **THEN** the response is `200 OK` and includes `token`, `expiresIn`, `user.username`, `user.displayName`, `user.role`, and `debug`, with `expiresIn` equal to 3600

#### Scenario: Invalid credentials keep their documented response
- **WHEN** a client submits credentials that do not match a user
- **THEN** the response is `401 Unauthorized` with `{"message":"Invalid username or password"}`

#### Scenario: Disallowed browser origin remains rejected
- **WHEN** a login request includes an `Origin` outside the allowlist for the active profile
- **THEN** the response is `403 Forbidden` without a response body

### Requirement: Successful login issues a signed JWT
Each token returned for a successful login MUST use HS256 and contain the `sub`, `role`, `iat`, and `exp` claims.

#### Scenario: Issued token has the expected claims and lifetime
- **WHEN** a client logs in with valid credentials
- **THEN** the returned token validates as an HS256 JWT with the authenticated username in `sub`, the account role in `role`, and an `exp` exactly 3600 seconds after `iat`

### Requirement: Token validation rejects invalid and legacy formats
Token validation MUST reject tokens whose signature is invalid or whose representation is not a valid signed JWT, including the former reversible format.

#### Scenario: Valid signed token is accepted
- **WHEN** a token has a valid HS256 signature and the required claims
- **THEN** validation accepts the token and returns its subject, role, issue time, and expiration

#### Scenario: Modified or forged signature is rejected
- **WHEN** a token's signed content is changed or it is signed with a different secret
- **THEN** validation rejects the token

#### Scenario: Legacy reversible token is rejected
- **WHEN** validation receives a token in the former unsigned, reversible format
- **THEN** validation rejects it

### Requirement: Token expiration is enforced
Validation MUST accept a correctly signed token only before its `exp` time and MUST reject it at or after expiration.

#### Scenario: Unexpired token is accepted
- **WHEN** validation receives a correctly signed token before its `exp` time
- **THEN** validation accepts it

#### Scenario: Expired token is rejected
- **WHEN** validation receives a correctly signed token at or after its `exp` time
- **THEN** validation rejects it

### Requirement: Signing secret remains external and confidential
The signing secret MUST be supplied through `AUTH_TOKEN_SECRET`, MUST NOT be hardcoded or versioned, and MUST NOT be exposed in HTTP responses or application logs.

#### Scenario: Missing or too-short signing secret is rejected
- **WHEN** the service starts without `AUTH_TOKEN_SECRET` or with a value shorter than the documented 32 UTF-8 bytes
- **THEN** the service does not start successfully

#### Scenario: Login does not disclose the signing secret
- **WHEN** a login succeeds using a configured signing secret
- **THEN** the response and application logs do not contain that secret