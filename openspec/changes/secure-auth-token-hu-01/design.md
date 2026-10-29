# Design

## Context

See [proposal.md](proposal.md) for motivation and scope, and [specs/auth-token/spec.md](specs/auth-token/spec.md) for the behavior contract.

`POST /auth/login` is exposed by `auth/`. `AuthService` authenticates credentials and requests a token from `JwtTokenService`; that service currently creates and verifies HS256 JWTs with `sub`, `role`, `iat`, and `exp`, enforces a 3600-second lifetime, and reads `auth.token.secret` from `AUTH_TOKEN_SECRET`. It rejects invalid signatures and expired or malformed tokens. The token implementation uses JDK HMAC primitives and JSON serialization; no separate token endpoint or middleware is part of the documented auth API.

`docs/ENDPOINTS.md` is the HTTP baseline: the login request and response shape, status codes, CORS behavior, and `expiresIn` remain unchanged. `AuthResourceTest` already characterizes successful login, claims, signature tampering, expiration, malformed tokens, and short-secret validation. It does not show a dedicated assertion for the former reversible token representation. Surefire supplies a synthetic test-only secret in `auth/pom.xml`; it is not a deployment secret and must never be replaced with a real credential.

## Goals / Non-Goals

**Goals:**
- Keep token guarantees inside the existing `auth/` boundary and meet the `auth-token` spec.
- Preserve the documented `POST /auth/login` request, response fields, status behavior, 3600-second TTL, and CORS behavior.
- Make the transition verifiable before changing any security-critical behavior, with deterministic time-based tests and no real secrets in tests or evidence.
- Limit follow-up code and tests to `auth/` and avoid dependencies or broad restructuring unless a verified gap requires them.

**Non-Goals:**
- Modify `api/`, `web/`, or their tests. The Python consumer named by Issue #14 remains a separate integration dependency.
- Change routes, payload fields, CORS, token claims, token lifetime, identity storage, or password handling.
- Add token refresh, key rotation, issuer/audience claims, a new authorization endpoint, or a dual-format migration window.
- Rework the authentication application or replace its persistence and service boundaries.

## Contracts Protected

- `POST /auth/login` remains compatible with `docs/ENDPOINTS.md`: the same request fields, response shape (including `debug`), documented error responses, and `expiresIn: 3600`.
- A successful login returns an HS256 JWT with `sub`, `role`, `iat`, and `exp`; its expiration is 3600 seconds after issue time.
- Token validation accepts only valid, unexpired signed tokens and rejects tampered, forged, malformed, expired, and legacy reversible tokens. There is no fallback to the legacy format.
- The deployment signing secret comes from `AUTH_TOKEN_SECRET`, is not committed or logged, and is not included in HTTP responses. The synthetic Surefire value is permitted only as an isolated test fixture.
- No HTTP contract or test coverage outside `auth/` is changed by this design.

## Components Affected

- `auth/src/main/java/.../AuthService.java`: login-to-token boundary; change only if a verified contract gap requires it.
- `auth/src/main/java/.../JwtTokenService.java`: token creation, verification, expiration, and secret validation; retain the existing service boundary.
- `auth/src/main/resources/application.properties`: environment-backed secret mapping; no secret value belongs here.
- `auth/src/test/java/.../AuthResourceTest.java` and related auth tests: characterization and token behavior checks.
- `auth/pom.xml`: test-only secret fixture is relevant to test isolation; no production secret or new dependency is planned.
- `api/` is an integration dependency, not an affected component in this change. Do not edit its source or tests here.

## Decisions

1. **Keep the existing auth token boundary and HTTP surface.** `AuthService` continues to delegate token work to `JwtTokenService`; the route and login response remain as documented. This is smaller and easier to verify than moving authentication into a new module, endpoint, or middleware, none of which is required by the spec.

2. **Use only the specified HS256 token contract and do not accept a legacy fallback.** The Issue and spec require a signed token, fixed claims, and enforcement of `exp`; accepting Base64 legacy values or adding a grace-period parser would preserve the unsafe behavior and make rejection ambiguous. Other algorithms and claim expansion are not part of this change.

3. **Retain environment-based secret configuration and the existing minimum-strength check.** `AUTH_TOKEN_SECRET` remains the runtime source, with the current minimum of 32 UTF-8 bytes. A code constant or checked-in deployment file is rejected because it exposes the signing key. The Surefire fixture remains synthetic and test-only; no real key may be used in tests, logs, or evidence.

4. **Do not add a JWT dependency or rewrite the parser without evidence.** The current JDK HMAC and JSON path can be tested against the narrow contract, avoiding dependency and migration risk. A JWT library is an alternative only if characterization uncovers a correctness gap that cannot be addressed within this existing boundary; evaluating or introducing it is not pre-authorized by this design.

5. **Use fixed instants for token-time tests.** Verify the boundary immediately before `exp` and at `exp` using the existing deterministic time seam, rather than sleeps or timing-sensitive integration tests. Keep HTTP-level tests for response compatibility and service-level tests for token validity.

## Incremental Strategy

1. Run the existing auth test suite and record the baseline before changing token behavior. Review existing tests first to avoid duplicating coverage.
2. Add characterization in `auth/` for the exact prior reversible token representation and assert that it is rejected. Before changing login handling, preserve the documented success response and representative validation, invalid-credential, and disallowed-origin responses.
3. Characterize the signing-secret boundary with isolated synthetic values: valid configured test secret, missing or too-short configuration, and checks that response/log output does not disclose the configured value. Never print the fixture or deployment value in test output.
4. Only after characterization, make the smallest auth-only change for any confirmed mismatch with the spec. The current implementation already appears to satisfy several token requirements; do not rewrite behavior that already passes the contract.
5. Run the auth tests from `auth/`, review modified-code coverage against the repository's auth target, and verify `docs/ENDPOINTS.md` still matches observed login behavior. Do not change API or web tests as part of this change.

## Risks / Trade-offs

- **[Issuer/consumer deployment mismatch]** A token-format cut can make the Python consumer reject newly issued tokens, or keep it accepting legacy values. Coordinate the consumer deployment as a separate integration action; this change adds no fallback and does not modify `api/`.
- **[Existing tokens become invalid]** Changing the signing secret invalidates tokens signed with the previous key, and a format cut rejects legacy tokens immediately. Keep the configured key stable for this change and schedule any key rotation separately; the 3600-second TTL bounds the intended token lifetime.
- **[Custom token parsing remains a maintenance risk]** Keeping the existing implementation avoids unrelated dependency churn but retains responsibility for strict algorithm, claim, signature, and time checks. The characterization suite must cover malformed and adversarial inputs required by the spec.
- **[Test fixture mistaken for a deployment secret]** The Surefire value is checked in by design for isolated tests. Keep its test-only purpose explicit and synthetic; production configuration must continue to come from the environment, and evidence must redact secret values.
- **[HTTP contract regression]** Token work can accidentally alter response fields or error mapping. Run the existing auth HTTP tests and preserve the `docs/ENDPOINTS.md` baseline before and after the change.

## Rollback

If an auth-only implementation change fails validation, revert that change and retain the last verified HS256 behavior and configured secret. Do not restore or re-enable acceptance of unsigned/reversible tokens as a rollback mechanism. If deployment has already switched a consumer, coordinate rollback across that integration boundary; otherwise tokens may be rejected until the compatible release is restored or the existing 3600-second tokens expire. Keep secret values outside the repository throughout rollback.