# Evaluación ReqFlow DoD: H-AUTH-10

**Fecha:** 2026-10-29  
**Repositorio y componente:** `user-bcp-student14/ai-labs`, `auth/`  
**Issue evaluado:** [#25 — Distinguir errores SQL de credenciales inválidas en POST /auth/login](https://github.com/user-bcp-student14/ai-labs/issues/25)  
**Cambio OpenSpec evaluado:** `secure-auth-token-hu-01`

## Resultado

El Issue #25 no puede declararse completo con la evidencia actual. El Issue trata H-AUTH-10 (la traducción de errores SQL durante autenticación), mientras que `secure-auth-token-hu-01` referencia el Issue #14 de otro repositorio y define el comportamiento de tokens JWT (H-AUTH-03). Las pruebas verdes y la cobertura de ese cambio no demuestran el criterio específico del Issue #25.

## Criterios

| Criterio | Estado | Evidencia | Observación |
|---|---|---|---|
| Issue y componente identificados | CUMPLIDO | GitHub Issue #25 está en `ai-labs`, tiene origen H-AUTH-10 y evidencia de `auth/`. | El Issue permanece abierto. |
| Trazabilidad Issue–OpenSpec | BLOQUEADO | [proposal.md](../openspec/changes/secure-auth-token-hu-01/proposal.md) referencia Issue #14 en `workshop-auth-backend-archive-taller-anterior`; la [spec](../openspec/changes/secure-auth-token-hu-01/specs/auth-token/spec.md) cubre JWT, expiración y secreto. | Este cambio no define ni implementa H-AUTH-10. |
| Contrato funcional de H-AUTH-10 | PENDIENTE DE OTRO GATE | Issue #25 indica que el resultado esperado ante `SQLException` requiere acuerdo. `docs/ENDPOINTS.md` documenta actualmente 401 para el error SQL de búsqueda. | Producto debe decidir si preserva 401 o define otro resultado antes de cambiar el contrato. |
| Prueba específica de fallo SQL | FALTA EVIDENCIA | El Issue exige inducir una `SQLException` y comprobar la respuesta acordada. Las pruebas ejecutadas del cambio documentan 46 pruebas generales; no hay evidencia de ese caso específico. | `mvn verify` verde no sustituye esta prueba de aceptación. |
| CI y suite local | CUMPLIDO | `mvn verify` desde `auth/`: 46 pruebas, 0 fallos, 0 errores, 0 omitidas; `BUILD SUCCESS`. | Resultado local para el componente. No equivale a checks de GitHub. |
| Cobertura crítica >= 85 % | CUMPLIDO | `tasks.md` registra JaCoCo con 186/196 líneas cubiertas (94,9 %) en `auth/`. | Es evidencia del componente; no prueba por sí sola el comportamiento específico H-AUTH-10. |
| Dependencias controladas | NO APLICA AL ALCANCE | Los artefactos `secure-auth-token-hu-01` no incorporan dependencias. | No se requiere gate de nuevas dependencias para este cambio. |
| Secure coding: secreto externo y sin exposición | CUMPLIDO | La spec/diseño exigen `AUTH_TOKEN_SECRET`; las pruebas de caracterización verifican configuración inválida y no divulgación del valor sintético. | No se usaron secretos reales. Esto corresponde al alcance del cambio de tokens, no resuelve H-AUTH-10. |
| PR final y revisión humana | PENDIENTE DE OTRO GATE | Issue #25 sigue abierto y su respuesta de GitHub indica `closed_by_pull_requests.total_count: 0`. | No hay evidencia de PR final ni de revisión humana para H-AUTH-10. |
| Checks de GitHub | FALTA EVIDENCIA | No hay un PR final vinculado al Issue #25 en la evidencia revisada. | No se dispone de checks remotos asociados para evaluarlos. |
| Riesgos y rollback | CUMPLIDO para `secure-auth-token-hu-01` | [design.md](../openspec/changes/secure-auth-token-hu-01/design.md) describe riesgos de integración y estrategia de rollback para tokens. | No aplica como evidencia de diseño/rollback de H-AUTH-10, que requiere un cambio alineado propio. |

## Evidencia OpenSpec

- [proposal.md](../openspec/changes/secure-auth-token-hu-01/proposal.md)
- [spec auth-token](../openspec/changes/secure-auth-token-hu-01/specs/auth-token/spec.md)
- [design.md](../openspec/changes/secure-auth-token-hu-01/design.md)
- [tasks.md](../openspec/changes/secure-auth-token-hu-01/tasks.md)

## Conclusión

El DoD del cambio de tokens tiene evidencia local de pruebas y cobertura, pero no corresponde al Issue #25. Para evaluar o cerrar H-AUTH-10 falta una definición aprobada del contrato ante fallo SQL, una prueba que provoque ese fallo, y el PR con revisión y checks requeridos. No se debe inferir que el Issue está resuelto a partir de `secure-auth-token-hu-01`.
