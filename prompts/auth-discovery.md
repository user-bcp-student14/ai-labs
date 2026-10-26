# Prompt adaptado: discovery de auth

## Alcance asumido

El foco, objetivo, criterios y formato del pedido original quedaron como
marcadores vacíos. Para no bloquear este primer discovery, se asumió como foco
la revisión del flujo completo de `auth/` frente a los candidatos de
`RETO.md`. Esta suposición no sustituye una aprobación humana del alcance.

- Leer documentación general solicitada y únicamente código de `auth/`.
- Verificar las rutas declaradas y contrastar cada candidato del reto con
  evidencia.
- No modificar código, configuración ni pruebas.
- No usar secretos reales ni generar payloads de explotación.
- Guardar evidencia del componente en `HALLAZGOS.md`, preservando cualquier
  contenido preexistente de otros componentes.

## Resultado buscado

Dejar una lista trazable de hechos observados, riesgos aún hipotéticos y
comprobaciones pendientes para auth, sin presentar los candidatos de
`RETO.md` como confirmados automáticamente.

## Criterios de aceptación provisionales

1. La ruta, el método HTTP, el formato observado y la prueba existente se
   registran con citas al código de `auth/`; los límites de lo cubierto por la
   prueba quedan explícitos.
2. Cada candidato de auth se separa entre hecho confirmado y verificación
   pendiente, con evidencia `auth/ruta:línea`; no se afirman advisories ni
   resultados de pruebas no ejecutadas.

## Formato de revisión provisional

Tabla por hallazgo con ID `H-AUTH-NN`, estado, hecho verificado, hipótesis o
riesgo por comprobar, evidencia y siguiente comprobación. Cerrar con ruta
observada, pruebas ejecutadas y evidencia faltante.

## Evidencia y límites de este paso

Se ejecutó `mvn test` desde `auth/`: 1 prueba pasó. No se consultaron fuentes
externas de advisories, no se ejecutó un análisis de cobertura y no se
verificaron consumidores ni configuración de despliegue fuera de `auth/`.
`docs/ENDPOINTS.md`, `docs/TESTING.md` y `PLAN.md` no existían durante este
paso; la documentación completa de endpoints y el plan corresponden a pasos
posteriores del laboratorio.
