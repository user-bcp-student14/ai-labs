# H-API-01: Investigación de inserción en tasks

## Issue y versión

- Issue: https://github.com/user-bcp-student14/ai-labs/issues/24, abierto.
- Componente: `api`, identificado por la ruta indicada en el issue.
- Commit base consultado mediante MCP: `ffd24873f979a29f242bb4f3c1a656948c7779b4`.
- El `HEAD` local coincide con el commit consultado y `origin` corresponde al repositorio del issue. El archivo `api/app/main.py` tiene un cambio local no confirmado: la última inserción añade `prueba` a la lista de columnas. La reproducción corresponde a ese commit **más ese cambio local**, no al commit limpio.
- SHA-256 del archivo local examinado: `2c354a9c5013befcd9a0388e0a2ac7f8a870fd0355caea77e49def3b39fb091a`.

## Hechos

- En el commit base, `api/app/main.py:37-43` crea `tasks` sin columna `prueba` e inserta tres registros cuando la tabla está vacía. La tercera inserción del commit no incluye `prueba`.
- En el archivo local, `api/app/main.py:42` añade `prueba` al tercer `INSERT`, sin añadir un valor. La definición de la tabla en `api/app/main.py:37` sigue sin esa columna.
- `api/tests/test_api.py:20-25` espera que la aplicación creada con una base nueva permita listar las tareas iniciales. El issue no proporciona traza ni datos de entrada.

## Hipótesis

- El tercer `INSERT` local falla en una tabla recién creada porque referencia una columna inexistente. **Confirmada para las sentencias SQL locales aisladas**, no para una ejecución completa de la aplicación ni como causa inequívoca del error original del issue.

## Reproducción

- Entorno: Windows, Python 3.13.15 y SQLite 3.50.4; solo biblioteca estándar. Base SQLite en memoria, sin importar la aplicación, servicios externos ni datos persistentes.
- Precondición: desde la raíz, verificar `git rev-parse HEAD`, `git status --short -- api` y `git diff -- api/app/main.py`; el SHA y el cambio local deben coincidir con los indicados arriba. No descartar modificaciones para conseguirlo.
- Desde el directorio `api`, ejecutar:

```powershell
python -c 'import ast, hashlib, platform, sqlite3, sys; from pathlib import Path; source = Path(sys.argv[1]).read_bytes(); tree = ast.parse(source); calls = sorted((node for node in ast.walk(tree) if isinstance(node, ast.Call) and node.lineno in (37, 40, 41, 42) and isinstance(node.func, ast.Attribute) and node.func.attr == sys.argv[3] and isinstance(node.func.value, ast.Name) and node.func.value.id == sys.argv[4]), key=lambda node: node.lineno); print(platform.python_version(), sqlite3.sqlite_version, hashlib.sha256(source).hexdigest(), flush=True); assert len(calls) == 4; connection = sqlite3.connect(sys.argv[2]); [(print(node.lineno, flush=True), connection.execute(ast.literal_eval(node.args[0]))) for node in calls]' app/main.py :memory: execute connection
```

- El comando extrae y ejecuta únicamente los cuatro literales SQL de las líneas indicadas, en orden; no ejecuta el código Python del archivo. Si el hash o las líneas cambian, esta evidencia ya no identifica la misma versión.
- Esperado: que la tabla nueva y sus tres inserciones se ejecuten sin excepción, como requiere la inicialización de `create_app` y el escenario de base nueva de `api/tests/test_api.py:20-25`.
- Observado, salida relevante sin la traza completa:

```text
3.13.15 3.50.4 2c354a9c5013befcd9a0388e0a2ac7f8a870fd0355caea77e49def3b39fb091a
37
40
41
42
sqlite3.OperationalError: table tasks has no column named prueba
Command exited with code 1
```

- Las líneas se imprimieron inmediatamente antes de ejecutar cada sentencia: la creación y las dos primeras inserciones terminaron; la tercera produjo la excepción. No se ejecutó la aplicación completa ni se probó una solución.