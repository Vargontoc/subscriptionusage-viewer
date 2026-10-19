# Contrato: stdio — `opencode debug paths data`

<!-- Origen: specs/openai-usage-monitor (REQ-003, NFR-003, DEC-003) -->

> Este contrato describe la cara observable del CLI `opencode` desde el punto de vista de `OpenCodeCliRunner` (`desktop/app/src/main/java/es/vargontoc/subscriptionviewer/usage/opencode/OpenCodeCliRunner.java`) para la consulta de la ruta del directorio de datos. La cara observable de `experimental.session.stats` se describe en `opencode-session-stats.md`.

## Participants

- **opencode-cli-runner** (`OpenCodeCliRunner.java`): ejecuta el CLI como proceso hijo del JDK con la lista de argumentos separados, timeout configurable (30 s por defecto, NFR-003), descarte de stderr, lectura concurrente de stdout con tope de 1 MiB y fallo silencioso (DEC-003, DEC-001).
- **CLI `opencode`**: ejecutable localizado por el PATH del sistema (constante `COMMAND = "opencode"`). Se invoca con `debug paths data`.
- **openai-credentials-reader** (`OpenAiCredentialsReader.java`): invoca `OpenCodeCliRunner.runDataPath()` solo cuando no existe `.codex/auth.json` y la sección `subscription-viewer.opencode` está configurada (DEC-002). Toma la ruta resultante como directorio cuyo `auth.json` contiene las credenciales de OpenAI.

## Channel

- **Lanzamiento:** `ProcessBuilder` con la lista de argumentos separados (sin shell, DEC-001). En Windows el runner antepone `cmd.exe /d /c opencode` para soportar envoltorios `.cmd`/`.ps1` (DEC-001).
- **stdin del proceso:** se cierra inmediatamente (no se envía nada).
- **stdout del proceso:** texto UTF-8. Debe ser una única línea con la ruta absoluta del directorio de datos de OpenCode. El runner lo lee en un hilo dedicado (`opencode-cli-stdout`) mientras el proceso corre, con un tope de 1 MiB; si se supera, devuelve un fallo silencioso (`salida-excesiva`).
- **stderr del proceso:** se descarta (`ProcessBuilder.Redirect.DISCARD`). Nunca aparece en el log del runner.
- **Timeout:** el runner espera hasta el plazo configurado (30 s por defecto, NFR-003). Al vencerse, destruye el proceso y sus descendientes y devuelve un fallo silencioso (`timeout`). En `runDataPath()` se usa el mismo timeout que para `session.stats`.

## Message 1 — `opencode-cli-runner` → CLI `opencode` (invocación)

El runner construye la siguiente línea de argumentos, en este orden, como elementos separados de la lista:

```
opencode debug paths data
```

| Posición | Argumento | Significado |
|---|---|---|
| 1 | `debug` | Subcomando del CLI. |
| 2 | `paths` | Operación que enumera rutas internas. |
| 3 | `data` | Pide la ruta del **directorio de datos**. |

Notas:

- En Windows el ejecutable puede ser un `.cmd` o `.ps1`. En ese caso el runner antepone `cmd.exe /d /c opencode` y concatena los tres argumentos anteriores sin alterarlos (DEC-001):
  ```
  cmd.exe /d /c opencode debug paths data
  ```
- No se pasan parámetros adicionales (`--param`) ni banderas.

## Message 2 — CLI `opencode` → `opencode-cli-runner` (salida en stdout)

La salida válida es **exactamente una línea** en `stdout`, terminada por un salto de línea final, con codificación UTF-8. Esa línea contiene la ruta absoluta del directorio de datos como cadena:

```
<directorio_de_datos_de_opencode>
```

### Ejemplo (con marcadores)

```
<directorio_de_datos_de_opencode>
```

`runDataPath()` aplica `trim()` y exige que `lines().count() == 1`. Por tanto:

- Una salida vacía (solo el salto de línea final) produce `CliResult.failure()` con categoría `ruta-no-disponible`.
- Una salida con varias líneas (por ejemplo, un mensaje de uso seguido de la ruta) produce `CliResult.failure()` con categoría `ruta-no-disponible`. Esta validación evita que un mensaje incidental de la CLI se use como ruta.

### Forma esperada

La cadena que devuelve la CLI es, en condiciones reales y para referencia operativa, una ruta absoluta del directorio de datos del usuario donde `opencode` persiste sus credenciales y su configuración. En este contrato se representa exclusivamente con el marcador `<directorio_de_datos_de_opencode>`, sin valores reales de ruta, para no filtrar información del entorno.

## Errors and absences (fallos silenciosos)

Toda condición adversa se traduce en un fallo silencioso del runner: la consulta no aporta ruta, la aplicación sigue en ejecución y **no se muestra ningún mensaje al usuario** (REQ-003, DEC-001). El único rastro es un único registro de log de nivel `debug` con una categoría, sin argumentos, sin stdout y sin stderr (DEC-011). Las categorías internas del runner que aplica este contrato son:

| Situación | Comportamiento |
|---|---|
| Ejecutable `opencode` no encontrado en PATH | `CliResult.failure()` con categoría `excepcion`. El proceso no arranca; `runDataPath()` devuelve el mismo resultado. |
| `opencode` termina con código de salida distinto de 0 | `CliResult.failure()` con categoría `codigo-salida`. |
| `opencode` no termina antes del timeout (30 s, NFR-003) | El runner destruye el proceso y sus descendientes y devuelve `CliResult.failure()` con categoría `timeout`. |
| `opencode` produce stdout con más de 1 MiB | El runner interrumpe la lectura y devuelve `CliResult.failure()` con categoría `salida-excesiva`. |
| Salida vacía (sin la ruta) | `CliResult.failure()` con categoría `ruta-no-disponible` (aceptado por la propuesta de TASK-014). |
| Salida con más de una línea | `CliResult.failure()` con categoría `ruta-no-disponible` (aceptado por la propuesta de TASK-014). |
| Hilo interrumpido | `CliResult.failure()` con categoría `interrumpido`; la marca de interrupción se propaga. |
| Cualquier otra excepción de E/S o de ejecución | `CliResult.failure()` con categoría `excepcion`. El texto o la excepción **no** se registran. |

`runDataPath()` devuelve `CliResult.failure()` (con `output()` igual a `""`) en todos los casos anteriores. El llamador (`openai-credentials-reader`) trata ese resultado como "no disponibles" sin error (ver contrato `file-json/desktop/app/opencode-auth.md`).

## Configuration (no es parte del stdio, pero fija el comportamiento)

- Nombre del ejecutable: `opencode` (constante `COMMAND`).
- Timeout por ejecución: configurable en `ConfigurationProvider.getOpenCodeCommandTimeout()` (30 s por defecto, NFR-003).
- Tamaño máximo de stdout aceptado por el runner: 1 MiB (`DEFAULT_MAX_OUTPUT_BYTES`).
- Variable de entorno `PATH`: la CLI se localiza por `PATH` del proceso. No se configura ruta alternativa.

## Concurrency

- `opencode` es de un solo proceso por consulta: `runDataPath()` lanza el comando, lee su stdout hasta que termina, y devuelve un único `CliResult`.
- El runner no comparte entre hilos los procesos abiertos para esta consulta.
- Una excepción lanzada en el hilo de lectura de stdout se captura y se traduce en `CliResult.failure()`; el hilo no propaga la excepción ni interrumpe a la JVM.

## Non-goals

- El contrato **no** describe la implementación interna de `opencode debug paths data` ni su formato fuera de la línea única.
- El contrato **no** describe el contenido del `auth.json` que se lee a continuación (ver `file-json/desktop/app/opencode-auth.md`).
- El contrato **no** describe los argumentos opcionales no documentados oficialmente. Si la CLI evoluciona y admite banderas adicionales, no se usan aquí.
- El contrato **no** cubre la invocación de `experimental.session.stats` (ver `opencode-session-stats.md`).
